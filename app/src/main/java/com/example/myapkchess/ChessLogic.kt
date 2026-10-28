package com.example.myapkchess

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object OpeningBook {
    fun getOpeningMove(opening: OpeningType, state: GameState): String? {
        val turnCount = state.moveHistory.size
        if (turnCount > 12) return null

        val isWhite = state.turn == PieceColor.WHITE

        val targetMoves = when (opening) {
            OpeningType.LONDON_SYSTEM -> if (isWhite) listOf("d2d4", "c1f4", "e2e3", "g1f3", "c2c3", "h2h3", "f1d3") else emptyList()
            OpeningType.SICILIAN_DEFENSE -> if (!isWhite) listOf("c7c5", "d7d6", "g8f6", "a7a6") else listOf("e2e4", "g1f3", "d2d4", "f1c4")
            OpeningType.CARO_KANN -> if (!isWhite) listOf("c7c6", "d7d5") else listOf("e2e4", "d2d4")
            OpeningType.KINGS_INDIAN -> if (!isWhite) listOf("g8f6", "g7g6", "f8g7", "d7d6", "e8g8") else listOf("d2d4", "c2c4", "b1c3", "e2e4")
            else -> emptyList()
        }

        val aiHistory = state.moveHistory.filter { it.pieceMoved.color == state.turn }.map {
            val fileFrom = ('a' + it.from.col)
            val rankFrom = 8 - it.from.row
            val fileTo = ('a' + it.to.col)
            val rankTo = 8 - it.to.row
            "$fileFrom$rankFrom$fileTo$rankTo"
        }

        for (move in targetMoves) {
            if (move !in aiHistory) {
                return move
            }
        }
        return null
    }
}

/**
 * Chess engine v2.
 *
 * What changed vs the original engine:
 *  - Fast, direct attack detection (pawn/knight/king/sliding rays) instead of
 *    "try every pseudo-legal move against the king" for every check test.
 *    This is what makes depth 3-4 search affordable on a phone.
 *  - Real evaluation: material + piece-square tables (tapered king table for
 *    the endgame), bishop pair, doubled/isolated/passed pawns, rooks on open
 *    files and pawn-shield king safety. The old eval was material only, plus a
 *    per-node dice roll for the "Random" play style, which made the search
 *    nondeterministic and blind.
 *  - Negamax alpha-beta with MVV-LVA move ordering, quiescence search over
 *    captures, check extensions on Expert, mate-distance scoring and
 *    iterative deepening under a per-level time budget, so a hard position
 *    can never freeze the game.
 *  - Promotions inside the search tree are always resolved (to queen) for
 *    BOTH sides; previously the human side's promotions were left as a pawn
 *    sitting on the last rank, so the engine could not see them coming.
 *  - Optional "force capture" rule (GameState.forceCapture, OFF by default):
 *    when enabled, the AI must capture whenever a capture is legal.
 *
 * Difficulty levels:
 *  1 Easy   - depth 1 + quiescence, generous move jitter and a 20% random
 *             move: plays sensible chess, develops pieces, takes free
 *             material, but misses quiet tactics and blunders sometimes.
 *             (The old Easy was a uniformly random move.)
 *  2 Medium - depth 2 + quiescence, small jitter, occasionally picks the
 *             second-best move.
 *  3 Hard   - depth 3 + quiescence, best move, no jitter.
 *  4 Expert - depth 4 + quiescence + check extensions, iterative deepening.
 */
object ChessLogic {

    // ------------------------- public move rules -------------------------

    fun getLegalMoves(position: Position, state: GameState): List<Position> {
        val piece = state.board[position] ?: return emptyList()
        if (piece.color != state.turn) return emptyList()

        val candidateMoves = mutableListOf<Position>()
        for (row in 0..7) {
            for (col in 0..7) {
                val target = Position(row, col)
                if (isValidMoveBasic(position, target, state)) {
                    if (!moveLeavesKingInCheck(position, target, state)) {
                        candidateMoves.add(target)
                    }
                }
            }
        }
        return candidateMoves
    }

    fun isValidMove(from: Position, to: Position, state: GameState): Boolean {
        return to in getLegalMoves(from, state)
    }

    fun isCheckmate(state: GameState): Boolean {
        if (!isKingInCheck(state.turn, state.board)) return false
        return !hasAnyLegalMoves(state)
    }

    fun isStalemate(state: GameState): Boolean {
        if (isKingInCheck(state.turn, state.board)) return false
        return !hasAnyLegalMoves(state)
    }

    /** Is [position] attacked by the opponent of [color]? Used by Help mode. */
    fun isDangerous(position: Position, color: PieceColor, state: GameState): Boolean {
        val opponentColor = if (color == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        return isSquareAttacked(position, opponentColor, state.board)
    }

    fun movePiece(from: Position, to: Position, state: GameState): GameState {
        val board = state.board.toMutableMap()
        val piece = board.remove(from)!!

        var isCastlingMove = false
        if (piece.type == PieceType.KING && abs(to.col - from.col) == 2) {
            isCastlingMove = true
            val rookFromCol = if (to.col > from.col) 7 else 0
            val rookToCol = if (to.col > from.col) to.col - 1 else to.col + 1
            val rook = board.remove(Position(from.row, rookFromCol))
            if (rook != null) {
                board[Position(from.row, rookToCol)] = rook.copy(hasMoved = true)
            }
        }

        val movedPiece = piece.copy(hasMoved = true)
        val captured = board.put(to, movedPiece)

        var pendingPromo = state.pendingPromotion

        if (movedPiece.type == PieceType.PAWN) {
            if ((movedPiece.color == PieceColor.WHITE && to.row == 0) ||
                (movedPiece.color == PieceColor.BLACK && to.row == 7)) {

                if (movedPiece.color != state.playerColor) {
                    board[to] = movedPiece.copy(type = PieceType.QUEEN)
                } else {
                    pendingPromo = to
                }
            }
        }

        val move = Move(from = from, to = to, pieceMoved = piece, pieceCaptured = captured, isCastling = isCastlingMove)
        return state.copy(
            board = board,
            turn = if (state.turn == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE,
            moveHistory = state.moveHistory + move,
            pendingPromotion = pendingPromo
        )
    }

    fun promotePawn(position: Position, newType: PieceType, state: GameState): GameState {
        val board = state.board.toMutableMap()
        val piece = board[position]
        if (piece != null) {
            board[position] = piece.copy(type = newType, hasMoved = true)
        }
        return state.copy(
            board = board,
            pendingPromotion = null
        )
    }

    // ------------------------- attack detection --------------------------

    private fun isKingInCheck(color: PieceColor, board: Map<Position, ChessPiece>): Boolean {
        val kingPos = board.entries.find { it.value.type == PieceType.KING && it.value.color == color }?.key ?: return false
        val opponentColor = if (color == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        return isSquareAttacked(kingPos, opponentColor, board)
    }

    /** Direct attack test: does [byColor] attack [pos] on [board]? */
    fun isSquareAttacked(pos: Position, byColor: PieceColor, board: Map<Position, ChessPiece>): Boolean {
        // Pawns: a white pawn attacks one row UP (row-1), so an attacking white
        // pawn sits one row BELOW the target (row+1); mirrored for black.
        val pawnRow = if (byColor == PieceColor.WHITE) pos.row + 1 else pos.row - 1
        if (pawnRow in 0..7) {
            for (dc in intArrayOf(-1, 1)) {
                val c = pos.col + dc
                if (c in 0..7) {
                    val p = board[Position(pawnRow, c)]
                    if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
                }
            }
        }

        // Knights
        for ((dr, dc) in KNIGHT_OFFSETS) {
            val r = pos.row + dr; val c = pos.col + dc
            if (r in 0..7 && c in 0..7) {
                val p = board[Position(r, c)]
                if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
            }
        }

        // King
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val r = pos.row + dr; val c = pos.col + dc
            if (r in 0..7 && c in 0..7) {
                val p = board[Position(r, c)]
                if (p != null && p.color == byColor && p.type == PieceType.KING) return true
            }
        }

        // Sliding pieces: walk each ray until the first piece.
        for ((dr, dc) in ROOK_DIRS) {
            if (rayAttacks(pos, dr, dc, byColor, board, PieceType.ROOK)) return true
        }
        for ((dr, dc) in BISHOP_DIRS) {
            if (rayAttacks(pos, dr, dc, byColor, board, PieceType.BISHOP)) return true
        }
        return false
    }

    private fun rayAttacks(pos: Position, dr: Int, dc: Int, byColor: PieceColor,
                           board: Map<Position, ChessPiece>, slider: PieceType): Boolean {
        var r = pos.row + dr
        var c = pos.col + dc
        while (r in 0..7 && c in 0..7) {
            val p = board[Position(r, c)]
            if (p != null) {
                return p.color == byColor && (p.type == slider || p.type == PieceType.QUEEN)
            }
            r += dr; c += dc
        }
        return false
    }

    private val KNIGHT_OFFSETS = listOf(
        -2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1
    )
    private val ROOK_DIRS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    private val BISHOP_DIRS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

    // ------------------------- pseudo-legal rules ------------------------

    private fun isValidMoveBasic(from: Position, to: Position, state: GameState): Boolean {
        val piece = state.board[from] ?: return false
        val targetPiece = state.board[to]
        if (targetPiece?.color == piece.color) return false

        return when (piece.type) {
            PieceType.PAWN -> validatePawnMove(from, to, piece, targetPiece, state)
            PieceType.ROOK -> validateRookMove(from, to, state)
            PieceType.KNIGHT -> validateKnightMove(from, to)
            PieceType.BISHOP -> validateBishopMove(from, to, state)
            PieceType.QUEEN -> validateQueenMove(from, to, state)
            PieceType.KING -> validateKingMove(from, to, piece, state)
        }
    }

    private fun moveLeavesKingInCheck(from: Position, to: Position, state: GameState): Boolean {
        val piece = state.board[from] ?: return false
        val nextBoard = state.board.toMutableMap()
        nextBoard.remove(from)
        nextBoard[to] = piece
        return isKingInCheck(piece.color, nextBoard)
    }

    private fun hasAnyLegalMoves(state: GameState): Boolean {
        for ((pos, piece) in state.board) {
            if (piece.color == state.turn) {
                if (getLegalMoves(pos, state).isNotEmpty()) return true
            }
        }
        return false
    }

    private fun validatePawnMove(from: Position, to: Position, piece: ChessPiece, target: ChessPiece?, state: GameState): Boolean {
        val direction = if (piece.color == PieceColor.WHITE) -1 else 1
        val dr = to.row - from.row
        val dc = to.col - from.col

        if (dc == 0) {
            if (target != null) return false
            if (dr == direction) return true
            if (dr == 2 * direction && ((piece.color == PieceColor.WHITE && from.row == 6) || (piece.color == PieceColor.BLACK && from.row == 1))) {
                val intermediate = Position(from.row + direction, from.col)
                return state.board[intermediate] == null
            }
        } else if (abs(dc) == 1 && dr == direction) {
            return target != null && target.color != piece.color
        }
        return false
    }

    private fun validateRookMove(from: Position, to: Position, state: GameState): Boolean {
        if (from.row != to.row && from.col != to.col) return false
        return isPathClear(from, to, state.board)
    }

    private fun validateKnightMove(from: Position, to: Position): Boolean {
        val dr = abs(to.row - from.row)
        val dc = abs(to.col - from.col)
        return (dr == 2 && dc == 1) || (dr == 1 && dc == 2)
    }

    private fun validateBishopMove(from: Position, to: Position, state: GameState): Boolean {
        if (abs(to.row - from.row) != abs(to.col - from.col)) return false
        return isPathClear(from, to, state.board)
    }

    private fun validateQueenMove(from: Position, to: Position, state: GameState): Boolean {
        if (from.row != to.row && from.col != to.col && abs(to.row - from.row) != abs(to.col - from.col)) return false
        return isPathClear(from, to, state.board)
    }

    private fun validateKingMove(from: Position, to: Position, piece: ChessPiece, state: GameState): Boolean {
        val dr = abs(to.row - from.row)
        val dc = abs(to.col - from.col)
        if (dr <= 1 && dc <= 1) return true

        if (dr == 0 && dc == 2 && !piece.hasMoved) {
            if (isKingInCheck(piece.color, state.board)) return false

            val direction = if (to.col > from.col) 1 else -1
            val rookCol = if (direction == 1) 7 else 0
            val rookPos = Position(from.row, rookCol)
            val rook = state.board[rookPos]

            if (rook == null || rook.type != PieceType.ROOK || rook.hasMoved) return false

            var c = from.col + direction
            while (c != rookCol) {
                if (state.board[Position(from.row, c)] != null) return false
                c += direction
            }

            val squareCrossed = Position(from.row, from.col + direction)
            if (isDangerous(squareCrossed, piece.color, state)) return false

            return true
        }
        return false
    }

    private fun isPathClear(from: Position, to: Position, board: Map<Position, ChessPiece>): Boolean {
        val dr = (to.row - from.row).signum()
        val dc = (to.col - from.col).signum()
        var r = from.row + dr
        var c = from.col + dc
        while (r != to.row || c != to.col) {
            if (board[Position(r, c)] != null) return false
            r += dr
            c += dc
        }
        return true
    }

    private fun Int.signum() = when {
        this > 0 -> 1
        this < 0 -> -1
        else -> 0
    }

    // ------------------------- opening book ------------------------------

    private fun getOpeningMove(state: GameState): GameState? {
        if (state.selectedOpening == OpeningType.RANDOM) return null

        val nextMoveStr = OpeningBook.getOpeningMove(state.selectedOpening, state) ?: return null

        val from = Position(8 - (nextMoveStr[1] - '0'), nextMoveStr[0] - 'a')
        val to = Position(8 - (nextMoveStr[3] - '0'), nextMoveStr[2] - 'a')

        val piece = state.board[from]
        if (piece != null && piece.color == state.turn) {
            if (to in getLegalMoves(from, state)) {
                return movePiece(from, to, state)
            }
        }
        return null
    }

    // ------------------------- evaluation --------------------------------
    // All scores in centipawns, from White's perspective unless stated.

    private fun pieceValue(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 100
        PieceType.KNIGHT -> 320
        PieceType.BISHOP -> 330
        PieceType.ROOK -> 500
        PieceType.QUEEN -> 900
        PieceType.KING -> 20000
    }

    // Piece-square tables, White's perspective, index = row * 8 + col with
    // row 0 = rank 8 (same orientation as the board model). Black pieces are
    // mirrored vertically.
    private val PST_PAWN = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0
    )
    private val PST_KNIGHT = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50
    )
    private val PST_BISHOP = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20
    )
    private val PST_ROOK = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 5, 5, 0, 0, 0
    )
    private val PST_QUEEN = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20
    )
    private val PST_KING_MG = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20
    )
    private val PST_KING_EG = intArrayOf(
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50
    )
    private val PASSED_PAWN_BONUS = intArrayOf(0, 10, 15, 25, 45, 80)

    private fun pst(table: IntArray, pos: Position, color: PieceColor): Int {
        return if (color == PieceColor.WHITE) table[pos.row * 8 + pos.col]
        else table[(7 - pos.row) * 8 + pos.col]
    }

    private fun evaluateWhite(state: GameState): Int {
        var score = 0
        var phase = 0
        var whiteBishops = 0
        var blackBishops = 0
        val whitePawnFiles = IntArray(8)
        val blackPawnFiles = IntArray(8)
        var whiteKing: Position? = null
        var blackKing: Position? = null

        // First pass: material + PST + bookkeeping.
        for ((pos, piece) in state.board) {
            when (piece.type) {
                PieceType.KNIGHT -> phase += 1
                PieceType.BISHOP -> phase += 1
                PieceType.ROOK -> phase += 2
                PieceType.QUEEN -> phase += 4
                else -> {}
            }
            val sign = if (piece.color == PieceColor.WHITE) 1 else -1
            var value = pieceValue(piece.type)
            value += when (piece.type) {
                PieceType.PAWN -> pst(PST_PAWN, pos, piece.color)
                PieceType.KNIGHT -> pst(PST_KNIGHT, pos, piece.color)
                PieceType.BISHOP -> pst(PST_BISHOP, pos, piece.color)
                PieceType.ROOK -> pst(PST_ROOK, pos, piece.color)
                PieceType.QUEEN -> pst(PST_QUEEN, pos, piece.color)
                PieceType.KING -> 0 // handled below, tapered
            }
            score += sign * value

            when {
                piece.type == PieceType.BISHOP && piece.color == PieceColor.WHITE -> whiteBishops++
                piece.type == PieceType.BISHOP -> blackBishops++
                piece.type == PieceType.PAWN && piece.color == PieceColor.WHITE -> whitePawnFiles[pos.col]++
                piece.type == PieceType.PAWN -> blackPawnFiles[pos.col]++
                piece.type == PieceType.KING && piece.color == PieceColor.WHITE -> whiteKing = pos
                piece.type == PieceType.KING -> blackKing = pos
            }

            // Play-style flavour, kept small so it can never wreck the search
            // (the old Aggressive bonus of 15/ply-rank swamped material).
            if (piece.type != PieceType.KING) {
                when (state.playStyle) {
                    PlayStyle.AGGRESSIVE -> {
                        val progress = if (piece.color == PieceColor.WHITE) 7 - pos.row else pos.row
                        score += sign * progress * 3
                    }
                    else -> {}
                }
            }
        }

        // Tapered king PST: middlegame table early, endgame table late.
        // phase runs 0 (bare kings) .. 24 (full board).
        if (whiteKing != null) {
            val mg = pst(PST_KING_MG, whiteKing, PieceColor.WHITE)
            val eg = pst(PST_KING_EG, whiteKing, PieceColor.WHITE)
            score += (mg * phase + eg * (24 - phase)) / 24
        }
        if (blackKing != null) {
            val mg = pst(PST_KING_MG, blackKing, PieceColor.BLACK)
            val eg = pst(PST_KING_EG, blackKing, PieceColor.BLACK)
            score -= (mg * phase + eg * (24 - phase)) / 24
        }

        // Bishop pair.
        if (whiteBishops >= 2) score += 30
        if (blackBishops >= 2) score -= 30

        // Pawn structure + rooks on files + king shield.
        score += pawnStructureScore(state, PieceColor.WHITE, whitePawnFiles, blackPawnFiles)
        score -= pawnStructureScore(state, PieceColor.BLACK, blackPawnFiles, whitePawnFiles)
        score += rookFileScore(state, PieceColor.WHITE, whitePawnFiles, blackPawnFiles)
        score -= rookFileScore(state, PieceColor.BLACK, blackPawnFiles, whitePawnFiles)
        if (phase > 6) { // king safety only matters while heavy pieces live
            score += kingShieldScore(state, whiteKing, PieceColor.WHITE)
            score -= kingShieldScore(state, blackKing, PieceColor.BLACK)
        }
        return score
    }

    /** Pawn-structure terms for one side, returned as a positive bonus. */
    private fun pawnStructureScore(state: GameState, color: PieceColor,
                                   ownFiles: IntArray, enemyFiles: IntArray): Int {
        var bonus = 0
        for (file in 0..7) {
            if (ownFiles[file] > 1) bonus -= (ownFiles[file] - 1) * 12 // doubled
            if (ownFiles[file] > 0) {
                val left = if (file > 0) ownFiles[file - 1] else 0
                val right = if (file < 7) ownFiles[file + 1] else 0
                if (left == 0 && right == 0) bonus -= 10 // isolated
            }
        }
        // Passed pawns: no enemy pawn ahead on this or the neighbouring files.
        for ((pos, piece) in state.board) {
            if (piece.type != PieceType.PAWN || piece.color != color) continue
            var passed = true
            for (f in max(0, pos.col - 1)..min(7, pos.col + 1)) {
                // scan enemy pawns on file f that sit ahead of this pawn
                for ((ePos, ePiece) in state.board) {
                    if (ePiece.type == PieceType.PAWN && ePiece.color != color && ePos.col == f) {
                        val ahead = if (color == PieceColor.WHITE) ePos.row < pos.row else ePos.row > pos.row
                        if (ahead) { passed = false; break }
                    }
                }
                if (!passed) break
            }
            if (passed) {
                val progress = if (color == PieceColor.WHITE) 6 - pos.row else pos.row - 1
                if (progress in PASSED_PAWN_BONUS.indices) bonus += PASSED_PAWN_BONUS[progress]
            }
        }
        return bonus
    }

    private fun rookFileScore(state: GameState, color: PieceColor,
                              ownFiles: IntArray, enemyFiles: IntArray): Int {
        var bonus = 0
        for ((pos, piece) in state.board) {
            if (piece.type != PieceType.ROOK || piece.color != color) continue
            if (ownFiles[pos.col] == 0 && enemyFiles[pos.col] == 0) bonus += 15      // open file
            else if (ownFiles[pos.col] == 0) bonus += 8                               // semi-open
        }
        return bonus
    }

    private fun kingShieldScore(state: GameState, king: Position?, color: PieceColor): Int {
        if (king == null) return 0
        var bonus = 0
        val shieldRow = if (color == PieceColor.WHITE) king.row - 1 else king.row + 1
        if (shieldRow in 0..7) {
            for (dc in -1..1) {
                val c = king.col + dc
                if (c in 0..7) {
                    val p = state.board[Position(shieldRow, c)]
                    if (p != null && p.type == PieceType.PAWN && p.color == color) {
                        bonus += if (state.playStyle == PlayStyle.DEFENSIVE) 14 else 9
                    }
                }
            }
        }
        return bonus
    }

    private fun evaluateForSideToMove(state: GameState): Int {
        val white = evaluateWhite(state)
        return if (state.turn == PieceColor.WHITE) white else -white
    }

    // ------------------------- search ------------------------------------

    private class SearchTimeout : Exception()

    private data class LevelConfig(
        val depth: Int,
        val budgetMs: Long,
        val useQuiescence: Boolean,
        val checkExtension: Boolean,
        val jitter: Int,               // Easy/Medium: pick randomly inside this window of best
        val randomMoveChance: Double,  // chance of a fully random legal move
        val secondBestChance: Double   // chance of taking the 2nd best move when close
    )

    private fun levelConfig(difficulty: Int): LevelConfig = when (difficulty) {
        1 -> LevelConfig(depth = 1, budgetMs = 800, useQuiescence = true, checkExtension = false,
            jitter = 70, randomMoveChance = 0.20, secondBestChance = 0.0)
        2 -> LevelConfig(depth = 2, budgetMs = 1_500, useQuiescence = true, checkExtension = false,
            jitter = 20, randomMoveChance = 0.0, secondBestChance = 0.10)
        4 -> LevelConfig(depth = 4, budgetMs = 6_000, useQuiescence = true, checkExtension = true,
            jitter = 0, randomMoveChance = 0.0, secondBestChance = 0.0)
        else -> LevelConfig(depth = 3, budgetMs = 3_000, useQuiescence = true, checkExtension = false,
            jitter = 0, randomMoveChance = 0.0, secondBestChance = 0.0) // 3 = Hard (and fallback)
    }

    private val MATE = 100_000
    private val INF = 1_000_000

    private var nodes = 0
    private var deadlineMs = 0L
    private var activeConfig = levelConfig(3)

    private fun checkClock() {
        if ((nodes and 2047) == 0 && System.currentTimeMillis() > deadlineMs) throw SearchTimeout()
    }

    private data class ScoredMove(val result: GameState, val order: Int, val isCapture: Boolean)

    /** A move inside the search: promotions are resolved immediately to queen. */
    private fun makeSearchMove(from: Position, to: Position, state: GameState): GameState {
        var next = movePiece(from, to, state)
        val promo = next.pendingPromotion
        if (promo != null) {
            next = promotePawn(promo, PieceType.QUEEN, next)
        }
        return next
    }

    private fun generateMoves(state: GameState, capturesOnly: Boolean = false): List<ScoredMove> {
        val result = mutableListOf<ScoredMove>()
        val pieces = state.board.filter { it.value.color == state.turn }
        for ((pos, piece) in pieces) {
            for (to in getLegalMoves(pos, state)) {
                val victim = state.board[to]
                val isCapture = victim != null
                if (capturesOnly && !isCapture) {
                    // Also let queen promotions through quiescence.
                    val isPromo = piece.type == PieceType.PAWN &&
                        ((piece.color == PieceColor.WHITE && to.row == 0) ||
                         (piece.color == PieceColor.BLACK && to.row == 7))
                    if (!isPromo) continue
                }
                var order = 0
                if (isCapture) {
                    // MVV-LVA: valuable victim, cheap attacker first.
                    order += 10_000 + pieceValue(victim!!.type) * 10 - pieceValue(piece.type) / 10
                }
                if (piece.type == PieceType.PAWN &&
                    ((piece.color == PieceColor.WHITE && to.row == 0) ||
                     (piece.color == PieceColor.BLACK && to.row == 7))) {
                    order += 9_000
                }
                if (piece.type == PieceType.KING && abs(to.col - pos.col) == 2) order += 300 // castling
                // Small PST-gain nudge so quiet developing moves order early too.
                val table = when (piece.type) {
                    PieceType.PAWN -> PST_PAWN; PieceType.KNIGHT -> PST_KNIGHT
                    PieceType.BISHOP -> PST_BISHOP; PieceType.ROOK -> PST_ROOK
                    PieceType.QUEEN -> PST_QUEEN; PieceType.KING -> PST_KING_MG
                }
                order += pst(table, to, piece.color) - pst(table, pos, piece.color)
                result.add(ScoredMove(makeSearchMove(pos, to, state), order, isCapture))
            }
        }
        result.sortByDescending { it.order }
        return result
    }

    /** Negamax alpha-beta. Score is from the side-to-move's perspective. */
    private fun negamax(state: GameState, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
        nodes++
        checkClock()
        if (depth <= 0) {
            return if (activeConfig.useQuiescence) quiescence(state, alphaIn, beta, ply, 0)
            else evaluateForSideToMove(state)
        }

        var alpha = alphaIn
        var searchDepth = depth
        val inCheck = isKingInCheck(state.turn, state.board)
        if (inCheck && activeConfig.checkExtension) searchDepth += 1

        val moves = generateMoves(state)
        if (moves.isEmpty()) {
            return if (inCheck) -MATE + ply else 0 // checkmate (prefer fast mates) / stalemate
        }

        var best = -INF
        for (move in moves) {
            val score = -negamax(move.result, searchDepth - 1, -beta, -alpha, ply + 1)
            if (score > best) best = score
            if (score > alpha) alpha = score
            if (alpha >= beta) break
        }
        return best
    }

    /** Quiescence: keep searching captures until the position is quiet. */
    private fun quiescence(state: GameState, alphaIn: Int, beta: Int, ply: Int, qDepth: Int): Int {
        nodes++
        checkClock()
        val standPat = evaluateForSideToMove(state)
        if (qDepth >= 6) return standPat
        if (standPat >= beta) return standPat
        var alpha = max(alphaIn, standPat)

        val captures = generateMoves(state, capturesOnly = true)
        if (captures.isEmpty()) {
            // No captures: if we are in check this could still be mate; the
            // caller at depth 0 cannot tell, but a mate score leaking in as
            // an eval would be worse, so stand pat.
            return standPat
        }
        for (move in captures) {
            val score = -quiescence(move.result, -beta, -alpha, ply + 1, qDepth + 1)
            if (score >= beta) return score
            if (score > alpha) alpha = score
        }
        return alpha
    }

    /** Score every candidate move at [depth]; returns (move, score) best-first. */
    private fun searchRoot(state: GameState, candidates: List<ScoredMove>, depth: Int): List<Pair<GameState, Int>> {
        val scored = mutableListOf<Pair<GameState, Int>>()
        var alpha = -INF
        for (move in candidates) {
            val score = -negamax(move.result, depth - 1, -INF, -alpha.coerceAtLeast(-INF), 1)
            scored.add(move.result to score)
            if (score > alpha) alpha = score
        }
        return scored.sortedByDescending { it.second }
    }

    fun getBestMove(state: GameState): GameState {
        if (isCheckmate(state) || isStalemate(state)) return state

        // Legal candidates first: the force-capture rule (when ON) and the
        // book both operate on this set.
        val allMoves = generateMoves(state)
        if (allMoves.isEmpty()) return state
        val captures = allMoves.filter { it.isCapture }
        val mustCapture = state.forceCapture && captures.isNotEmpty()
        val candidates = if (mustCapture) captures else allMoves

        // Opening book (skipped if it would break the force-capture rule).
        val bookMove = getOpeningMove(state)
        if (bookMove != null) {
            val bookIsCapture = bookMove.moveHistory.lastOrNull()?.pieceCaptured != null
            if (!mustCapture || bookIsCapture) return bookMove
        }

        val config = levelConfig(state.difficulty)
        activeConfig = config
        nodes = 0
        deadlineMs = System.currentTimeMillis() + config.budgetMs

        var scored: List<Pair<GameState, Int>>? = null
        try {
            // Iterative deepening: keep the best completed iteration if the
            // clock cuts a deeper one short.
            for (depth in 1..config.depth) {
                scored = searchRoot(state, candidates, depth)
            }
        } catch (e: SearchTimeout) {
            // Fall through with the last completed iteration (or null).
        }
        val finalScores = scored ?: run {
            // Even depth 1 timed out (should not happen): fall back to the
            // move-ordering favourite so we always return a legal move.
            return candidates.first().result
        }
        if (finalScores.isEmpty()) return candidates.first().result

        // Difficulty flavour at the root only (never inside the search).
        if (config.randomMoveChance > 0 && Math.random() < config.randomMoveChance) {
            return candidates.random().result
        }
        if (config.jitter > 0) {
            val best = finalScores.first().second
            val pool = finalScores.filter { best - it.second <= config.jitter }
            return pool.random().first
        }
        if (config.secondBestChance > 0 && finalScores.size > 1 &&
            Math.random() < config.secondBestChance &&
            finalScores[0].second - finalScores[1].second <= 60) {
            return finalScores[1].first
        }
        return finalScores.first().first
    }
}
