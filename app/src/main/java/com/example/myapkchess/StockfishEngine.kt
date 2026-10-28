package com.example.myapkchess

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Stockfish 19 (GPLv3, see STOCKFISH.md) running as a separate process and
 * spoken to over UCI. This file is deliberately free of Android imports so
 * the whole engine path can be exercised on a desktop JVM against the Linux
 * Stockfish binary; on Android, MainActivity calls [init] once with the
 * path of the bundled binary inside the app's native library directory.
 *
 * The app's own rules remain the source of truth: the position is sent as
 * FEN (castling rights from the pieces' hasMoved flags; en passant is
 * always "-" because this app's rules have no en passant), and the move
 * Stockfish returns is validated against the app's own legal-move list
 * before it is applied. Any failure at any step returns null, and the
 * caller falls back to the built-in Kotlin engine.
 */
object StockfishEngine {

    @Volatile
    var enginePath: String? = null
        private set

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var lines: LinkedBlockingQueue<String>? = null

    fun init(path: String) {
        enginePath = path
    }

    fun isAvailable(): Boolean = enginePath != null

    // ------------------------- UCI plumbing -------------------------

    @Synchronized
    private fun ensureStarted(): Boolean {
        if (process?.isAlive == true && writer != null) return true
        val path = enginePath ?: return false
        shutdownLocked()
        return try {
            val p = ProcessBuilder(path).redirectErrorStream(true).start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream))
            val queue = LinkedBlockingQueue<String>()
            lines = queue
            val reader = BufferedReader(InputStreamReader(p.inputStream))
            Thread {
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        queue.offer(line)
                    }
                } catch (_: Exception) {
                    // Process died; the queue simply stops receiving lines.
                }
            }.apply { isDaemon = true; name = "stockfish-reader" }.start()

            send("uci")
            if (!waitFor("uciok", 15_000)) return failStart()
            send("setoption name Threads value 2")
            send("setoption name Hash value 64")
            send("isready")
            if (!waitFor("readyok", 15_000)) return failStart()
            true
        } catch (e: Exception) {
            failStart()
        }
    }

    private fun failStart(): Boolean {
        shutdownLocked()
        return false
    }

    private fun send(cmd: String) {
        val w = writer ?: throw IllegalStateException("engine not started")
        w.write(cmd)
        w.newLine()
        w.flush()
    }

    /** Consume engine output until [token] appears (whole-line match or prefix), or timeout. */
    private fun waitFor(token: String, timeoutMs: Long): Boolean {
        val q = lines ?: return false
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return false
            val line = q.poll(remaining, TimeUnit.MILLISECONDS) ?: return false
            if (line.trim() == token) return true
        }
    }

    /** Read until a bestmove line; returns the move token or null on timeout. */
    private fun awaitBestMove(timeoutMs: Long): String? {
        val q = lines ?: return null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return null
            val line = q.poll(remaining, TimeUnit.MILLISECONDS) ?: return null
            if (line.startsWith("bestmove")) {
                val mv = line.split(Regex("\\s+")).getOrNull(1)
                return if (mv == null || mv == "(none)") null else mv
            }
        }
    }

    @Synchronized
    fun shutdown() = shutdownLocked()

    private fun shutdownLocked() {
        try { writer?.let { it.write("quit"); it.newLine(); it.flush() } } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        process = null
        writer = null
        lines = null
    }

    // ------------------------- difficulty ---------------------------

    private data class SfLevel(val skill: Int, val movetimeMs: Int)

    private fun levelFor(difficulty: Int): SfLevel = when (difficulty) {
        1 -> SfLevel(skill = 5, movetimeMs = 300)     // Easy
        2 -> SfLevel(skill = 11, movetimeMs = 600)    // Medium
        4 -> SfLevel(skill = 20, movetimeMs = 2_500)  // Expert
        else -> SfLevel(skill = 17, movetimeMs = 1_200) // Hard (3, and fallback)
    }

    // ------------------------- public API ---------------------------

    /**
     * Returns the state after Stockfish's chosen move, or null if the
     * engine is unavailable or anything went wrong (caller falls back to
     * the built-in engine). Blocking: call from a background thread.
     */
    @Synchronized
    fun getBestMove(state: GameState): GameState? {
        if (ChessLogic.isCheckmate(state) || ChessLogic.isStalemate(state)) return null

        // Opening book first (same behaviour as the built-in engine path),
        // but never let the book break the force-capture rule.
        val legalCaptures = captureMovesUci(state)
        val mustCapture = state.forceCapture && legalCaptures.isNotEmpty()
        bookMove(state)?.let { afterBook ->
            val bookWasCapture = afterBook.moveHistory.lastOrNull()?.pieceCaptured != null
            if (!mustCapture || bookWasCapture) return afterBook
        }

        if (!ensureStarted()) return null
        val level = levelFor(state.difficulty)
        return try {
            if (state.kingOfTheHill) {
                return kothMove(state, level, mustCapture, legalCaptures)
            }
            send("setoption name Skill Level value ${level.skill}")
            send("position fen ${toFen(state)}")
            if (mustCapture) {
                send("go movetime ${level.movetimeMs} searchmoves ${legalCaptures.joinToString(" ")}")
            } else {
                send("go movetime ${level.movetimeMs}")
            }
            val best = awaitBestMove(level.movetimeMs + 20_000L) ?: run {
                shutdownLocked()
                return null
            }
            applyUciMove(state, best)
        } catch (e: Exception) {
            shutdownLocked()
            null
        }
    }

    // ------------------------- book ---------------------------------

    private fun bookMove(state: GameState): GameState? {
        if (state.selectedOpening == OpeningType.RANDOM) return null
        val nextMoveStr = OpeningBook.getOpeningMove(state.selectedOpening, state) ?: return null
        if (nextMoveStr.length < 4) return null
        val from = Position(8 - (nextMoveStr[1] - '0'), nextMoveStr[0] - 'a')
        val to = Position(8 - (nextMoveStr[3] - '0'), nextMoveStr[2] - 'a')
        val piece = state.board[from]
        return if (piece != null && piece.color == state.turn && to in ChessLogic.getLegalMoves(from, state)) {
            ChessLogic.movePiece(from, to, state)
        } else null
    }

    // ------------------------- FEN ----------------------------------

    private fun pieceLetter(piece: ChessPiece): Char {
        val c = when (piece.type) {
            PieceType.PAWN -> 'p'
            PieceType.KNIGHT -> 'n'
            PieceType.BISHOP -> 'b'
            PieceType.ROOK -> 'r'
            PieceType.QUEEN -> 'q'
            PieceType.KING -> 'k'
        }
        return if (piece.color == PieceColor.WHITE) c.uppercaseChar() else c
    }

    /** FEN for [state] under this app's rules: no en passant, ever. */
    fun toFen(state: GameState): String {
        val sb = StringBuilder()
        for (row in 0..7) {
            var empty = 0
            for (col in 0..7) {
                val piece = state.board[Position(row, col)]
                if (piece == null) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(pieceLetter(piece))
                }
            }
            if (empty > 0) sb.append(empty)
            if (row < 7) sb.append('/')
        }
        sb.append(if (state.turn == PieceColor.WHITE) " w " else " b ")

        // Castling rights from hasMoved flags + piece presence.
        fun unmoved(pos: Position, type: PieceType, color: PieceColor): Boolean {
            val p = state.board[pos]
            return p != null && p.type == type && p.color == color && !p.hasMoved
        }
        val castling = StringBuilder()
        if (unmoved(Position(7, 4), PieceType.KING, PieceColor.WHITE) &&
            unmoved(Position(7, 7), PieceType.ROOK, PieceColor.WHITE)) castling.append('K')
        if (unmoved(Position(7, 4), PieceType.KING, PieceColor.WHITE) &&
            unmoved(Position(7, 0), PieceType.ROOK, PieceColor.WHITE)) castling.append('Q')
        if (unmoved(Position(0, 4), PieceType.KING, PieceColor.BLACK) &&
            unmoved(Position(0, 7), PieceType.ROOK, PieceColor.BLACK)) castling.append('k')
        if (unmoved(Position(0, 4), PieceType.KING, PieceColor.BLACK) &&
            unmoved(Position(0, 0), PieceType.ROOK, PieceColor.BLACK)) castling.append('q')
        sb.append(if (castling.isEmpty()) "-" else castling.toString())

        sb.append(" - ") // en passant: not part of this app's rules

        var halfmove = 0
        for (mv in state.moveHistory.asReversed()) {
            if (mv.pieceMoved.type == PieceType.PAWN || mv.pieceCaptured != null) break
            halfmove++
        }
        val fullmove = state.moveHistory.size / 2 + 1
        sb.append(halfmove).append(' ').append(fullmove)
        return sb.toString()
    }

    // ------------------- King of the Hill layer ---------------------
    //
    // Official Stockfish only plays standard chess, so the variant is
    // enforced here, around the engine:
    //  1. A legal king move onto the hill is an immediate win: play it
    //     without consulting Stockfish (it outranks even the
    //     force-capture house rule, because it ends the game).
    //  2. Any move that lets the opponent win on the hill next move is
    //     removed from the candidate pool when an alternative exists —
    //     in BOTH stances.
    //  3. Stockfish then ranks the surviving pool (MultiPV); the stance
    //     breaks near-ties: OFFENSIVE prefers the move that brings the
    //     AI's own king closest to the hill, DEFENSIVE the move that
    //     leaves the opponent's king farthest from it.

    private data class KothCand(val uci: String, val result: GameState)

    private fun kothMove(
        state: GameState,
        level: SfLevel,
        mustCapture: Boolean,
        legalCaptures: List<String>
    ): GameState? {
        val cands = mutableListOf<KothCand>()
        for ((pos, piece) in state.board) {
            if (piece.color != state.turn) continue
            for (to in ChessLogic.getLegalMoves(pos, state)) {
                var r = ChessLogic.movePiece(pos, to, state)
                val isPromo = r.pendingPromotion != null
                if (isPromo) r = ChessLogic.promotePawn(r.pendingPromotion!!, PieceType.QUEEN, r)
                cands.add(KothCand(squareUci(pos) + squareUci(to) + if (isPromo) "q" else "", r))
            }
        }
        if (cands.isEmpty()) return null

        // 1. Immediate hill win.
        cands.firstOrNull { ChessLogic.hillWinner(it.result) == state.turn }?.let { return it.result }

        // 2. Never allow an immediate hill loss when avoidable.
        var pool = cands.filter { !ChessLogic.sideToMoveHasHillWin(it.result) }
        if (pool.isEmpty()) pool = cands

        // 3. Force-capture still applies inside the variant.
        if (mustCapture) {
            val capSet = legalCaptures.toSet()
            val caps = pool.filter { it.uci in capSet }
            if (caps.isNotEmpty()) pool = caps
        }

        // 4. Stockfish ranks the pool; stance breaks near-ties.
        send("setoption name Skill Level value ${level.skill}")
        send("setoption name MultiPV value ${minOf(4, pool.size)}")
        send("position fen ${toFen(state)}")
        send("go movetime ${level.movetimeMs} searchmoves ${pool.joinToString(" ") { it.uci }}")
        val (bestUci, scores) = awaitBestMoveWithScores(level.movetimeMs + 20_000L)
        try { send("setoption name MultiPV value 1") } catch (_: Exception) {}
        if (bestUci == null) {
            shutdownLocked()
            return null
        }
        val chosen = chooseWithStance(state, pool, scores, bestUci)
        return pool.firstOrNull { it.uci == chosen }?.result ?: applyUciMove(state, chosen)
    }

    /** Like awaitBestMove, but also collects the latest MultiPV scores (centipawns, side-to-move view). */
    private fun awaitBestMoveWithScores(timeoutMs: Long): Pair<String?, Map<String, Int>> {
        val q = lines ?: return null to emptyMap()
        val scores = mutableMapOf<String, Int>()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return null to scores
            val line = q.poll(remaining, TimeUnit.MILLISECONDS) ?: return null to scores
            if (line.startsWith("info") && line.contains(" multipv ")) {
                val parts = line.split(Regex("\\s+"))
                val pvIdx = parts.indexOf("pv")
                if (pvIdx >= 0 && pvIdx + 1 < parts.size) {
                    val move = parts[pvIdx + 1]
                    val cpIdx = parts.indexOf("cp")
                    val mateIdx = parts.indexOf("mate")
                    when {
                        cpIdx >= 0 && cpIdx + 1 < parts.size ->
                            parts[cpIdx + 1].toIntOrNull()?.let { scores[move] = it }
                        mateIdx >= 0 && mateIdx + 1 < parts.size ->
                            parts[mateIdx + 1].toIntOrNull()?.let {
                                scores[move] = if (it > 0) 100_000 - it else -100_000 - it
                            }
                    }
                }
            }
            if (line.startsWith("bestmove")) {
                val mv = line.split(Regex("\\s+")).getOrNull(1)
                return (if (mv == null || mv == "(none)") null else mv) to scores
            }
        }
    }

    private fun chooseWithStance(
        state: GameState,
        pool: List<KothCand>,
        scores: Map<String, Int>,
        bestUci: String
    ): String {
        val bestScore = scores[bestUci] ?: return bestUci
        val close = pool.filter { (scores[it.uci] ?: Int.MIN_VALUE) >= bestScore - 120 }
        if (close.size <= 1) return bestUci
        val opponent = if (state.turn == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        return when (state.kothStance) {
            KothStance.OFFENSIVE -> close.minWithOrNull(
                compareBy<KothCand> { cand ->
                    ChessLogic.kingOf(cand.result.board, state.turn)
                        ?.let { ChessLogic.hillDistance(it) } ?: 99
                }.thenByDescending { scores[it.uci] ?: Int.MIN_VALUE }
            )?.uci ?: bestUci
            KothStance.DEFENSIVE -> close.maxWithOrNull(
                compareBy<KothCand> { cand ->
                    ChessLogic.kingOf(cand.result.board, opponent)
                        ?.let { ChessLogic.hillDistance(it) } ?: 0
                }.thenBy { scores[it.uci] ?: Int.MIN_VALUE }
            )?.uci ?: bestUci
        }
    }

    // ------------------------- move conversion ----------------------

    private fun squareUci(pos: Position): String = "${'a' + pos.col}${8 - pos.row}"

    private fun parseSquare(file: Char, rank: Char): Position =
        Position(8 - (rank - '0'), file - 'a')

    /** All legal captures for the side to move, in UCI notation (promotions expanded). */
    fun captureMovesUci(state: GameState): List<String> {
        val result = mutableListOf<String>()
        for ((pos, piece) in state.board) {
            if (piece.color != state.turn) continue
            for (to in ChessLogic.getLegalMoves(pos, state)) {
                if (state.board[to] == null) continue
                val base = squareUci(pos) + squareUci(to)
                val isPromo = piece.type == PieceType.PAWN &&
                    ((piece.color == PieceColor.WHITE && to.row == 0) ||
                     (piece.color == PieceColor.BLACK && to.row == 7))
                if (isPromo) {
                    for (suffix in charArrayOf('q', 'r', 'b', 'n')) result.add(base + suffix)
                } else {
                    result.add(base)
                }
            }
        }
        return result
    }

    /** Validate Stockfish's UCI move against the app's own rules, then apply it. */
    fun applyUciMove(state: GameState, uci: String): GameState? {
        if (uci.length < 4) return null
        val from = parseSquare(uci[0], uci[1])
        val to = parseSquare(uci[2], uci[3])
        val piece = state.board[from] ?: return null
        if (piece.color != state.turn) return null
        if (to !in ChessLogic.getLegalMoves(from, state)) return null
        var next = ChessLogic.movePiece(from, to, state)
        val promoPos = next.pendingPromotion
        if (promoPos != null) {
            val promoType = when (uci.getOrNull(4)) {
                'q' -> PieceType.QUEEN
                'r' -> PieceType.ROOK
                'b' -> PieceType.BISHOP
                'n' -> PieceType.KNIGHT
                else -> PieceType.QUEEN
            }
            next = ChessLogic.promotePawn(promoPos, promoType, next)
        }
        return next
    }
}
