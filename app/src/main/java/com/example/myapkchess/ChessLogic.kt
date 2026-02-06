package com.example.myapkchess

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ChessLogic {
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

        // Add Castling
        if (piece.type == PieceType.KING && !piece.hasMoved) {
            val row = if (piece.color == PieceColor.WHITE) 7 else 0
            // Kingside (O-O)
            val rookKingside = state.board[Position(row, 7)]
            if (rookKingside?.type == PieceType.ROOK && !rookKingside.hasMoved) {
                if (state.board[Position(row, 5)] == null && state.board[Position(row, 6)] == null) {
                    if (!isKingInCheck(piece.color, state.board) &&
                        !isSquareAttacked(Position(row, 5), piece.color, state.board) &&
                        !isSquareAttacked(Position(row, 6), piece.color, state.board)) {
                        candidateMoves.add(Position(row, 6))
                    }
                }
            }
            // Queenside (O-O-O)
            val rookQueenside = state.board[Position(row, 0)]
            if (rookQueenside?.type == PieceType.ROOK && !rookQueenside.hasMoved) {
                if (state.board[Position(row, 1)] == null && state.board[Position(row, 2)] == null && state.board[Position(row, 3)] == null) {
                    if (!isKingInCheck(piece.color, state.board) &&
                        !isSquareAttacked(Position(row, 3), piece.color, state.board) &&
                        !isSquareAttacked(Position(row, 2), piece.color, state.board)) {
                        candidateMoves.add(Position(row, 2))
                    }
                }
            }
        }

        return candidateMoves
    }

    private fun isSquareAttacked(pos: Position, color: PieceColor, board: Map<Position, ChessPiece>): Boolean {
        val opponentColor = if (color == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        val tempState = GameState(board = board, turn = opponentColor)
        for ((p, piece) in board) {
            if (piece.color == opponentColor) {
                if (piece.type == PieceType.PAWN) {
                    val direction = if (piece.color == PieceColor.WHITE) -1 else 1
                    if (pos.row == p.row + direction && abs(pos.col - p.col) == 1) return true
                } else if (isValidMoveBasic(p, pos, tempState)) {
                    return true
                }
            }
        }
        return false
    }

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
            PieceType.KING -> validateKingMove(from, to)
        }
    }

    private fun moveLeavesKingInCheck(from: Position, to: Position, state: GameState): Boolean {
        val piece = state.board[from] ?: return false
        val nextBoard = state.board.toMutableMap()
        nextBoard.remove(from)
        nextBoard[to] = piece
        
        return isKingInCheck(piece.color, nextBoard)
    }

    fun isKingInCheck(color: PieceColor, board: Map<Position, ChessPiece>): Boolean {
        val kingPos = board.entries.find { it.value.type == PieceType.KING && it.value.color == color }?.key ?: return false
        val opponentColor = if (color == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        
        for ((pos, piece) in board) {
            if (piece.color == opponentColor) {
                if (piece.type == PieceType.PAWN) {
                    val direction = if (piece.color == PieceColor.WHITE) -1 else 1
                    if (kingPos.row == pos.row + direction && abs(kingPos.col - pos.col) == 1) return true
                } else if (isValidMoveBasic(pos, kingPos, state = GameState(board = board, turn = opponentColor))) {
                    return true
                }
            }
        }
        return false
    }

    fun isCheckmate(state: GameState): Boolean {
        if (!isKingInCheck(state.turn, state.board)) return false
        return !hasAnyLegalMoves(state)
    }

    fun isStalemate(state: GameState): Boolean {
        if (isKingInCheck(state.turn, state.board)) return false
        return !hasAnyLegalMoves(state)
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

    private fun validateKingMove(from: Position, to: Position): Boolean {
        return abs(to.row - from.row) <= 1 && abs(to.col - from.col) <= 1
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

    fun isDangerous(position: Position, color: PieceColor, state: GameState): Boolean {
        return isSquareAttacked(position, color, state.board)
    }

    fun getBestMove(state: GameState): GameState {
        if (isCheckmate(state) || isStalemate(state)) return state
        
        val depth = when (state.difficulty) {
            1 -> 0
            2 -> 1
            3 -> 2
            else -> 1
        }

        val bestAIState = if (depth == 0) makeRandomMove(state) else makeHeuristicMove(state, depth)
        val currentBoardValue = evaluateBoard(state, state.turn)
        val bestAIValue = bestAIState?.let { evaluateBoard(it, state.turn) } ?: Int.MIN_VALUE

        val awesomeOpportunity = (bestAIValue - currentBoardValue) > 15

        if (!awesomeOpportunity && !isKingInCheck(state.turn, state.board)) {
            val openingState = tryOpeningMove(state)
            if (openingState != null) {
                val bookMove = openingState.moveHistory.last()
                if (!isDangerous(bookMove.to, state.turn, openingState)) {
                    return openingState
                }
            }
        }

        return bestAIState ?: state
    }

    private fun tryOpeningMove(state: GameState): GameState? {
        val aiColor = state.turn
        val moveCount = state.moveHistory.size
        if (moveCount >= 20) return null

        val opening = if (state.selectedOpening == OpeningType.RANDOM) {
            val randoms = OpeningType.values().filter { it != OpeningType.RANDOM }
            randoms.getOrElse(state.moveHistory.hashCode().let { if(it<0) -it else it } % randoms.size) { OpeningType.LONDON }
        } else {
            state.selectedOpening
        }

        val openingSequence: List<Pair<Position, Position>> = when (opening) {
            OpeningType.LONDON -> listOf(
                Position(6, 3) to Position(4, 3), // 1. d4
                Position(7, 6) to Position(5, 5), // 2. Nf3
                Position(7, 2) to Position(4, 5), // 3. Bf4
                Position(6, 4) to Position(5, 4), // 4. e3
                Position(6, 2) to Position(5, 2), // 5. c3
                Position(7, 5) to Position(5, 3), // 6. Bd3
                Position(7, 1) to Position(5, 2), // 7. Nc3
                Position(7, 4) to Position(7, 6), // 8. O-O
            )
            OpeningType.SICILIAN -> listOf(
                Position(6, 4) to Position(4, 4), // 1. e4
                Position(7, 6) to Position(5, 5), // 2. Nf3
                Position(6, 3) to Position(4, 3), // 3. d4
            )
            OpeningType.KINGS_INDIAN -> listOf(
                Position(6, 3) to Position(4, 3), // 1. d4
                Position(6, 2) to Position(4, 2), // 2. c4
                Position(7, 1) to Position(5, 2), // 3. Nc3
                Position(6, 4) to Position(4, 4), // 4. e4
            )
            OpeningType.FRENCH -> listOf(
                Position(6, 4) to Position(4, 4), // 1. e4
                Position(6, 3) to Position(4, 3), // 2. d4
                Position(4, 4) to Position(3, 4), // 3. e5
            )
            OpeningType.RUI_LOPEZ -> listOf(
                Position(6, 4) to Position(4, 4), // 1. e4
                Position(7, 6) to Position(5, 5), // 2. Nf3
                Position(7, 5) to Position(3, 1), // 3. Bb5
                Position(7, 4) to Position(7, 6), // 4. O-O
            )
            OpeningType.CARO_KANN -> listOf(
                Position(6, 4) to Position(4, 4), // 1. e4
                Position(6, 3) to Position(4, 3), // 2. d4
                Position(7, 1) to Position(5, 2), // 3. Nc3
            )
            OpeningType.ITALIAN -> listOf(
                Position(6, 4) to Position(4, 4), // 1. e4
                Position(7, 6) to Position(5, 5), // 2. Nf3
                Position(7, 5) to Position(4, 2), // 3. Bc4
                Position(7, 4) to Position(7, 6), // 4. O-O
            )
            OpeningType.SCANDINAVIAN -> listOf(
                Position(6, 4) to Position(4, 4), // 1. e4
            )
            OpeningType.QUEENS_GAMBIT -> listOf(
                Position(6, 3) to Position(4, 3), // 1. d4
                Position(6, 2) to Position(4, 2), // 2. c4
            )
            else -> emptyList()
        }

        val aiOpeningMoves = openingSequence.map { (from, to) ->
            if (aiColor == PieceColor.BLACK) {
                Position(7 - from.row, from.col) to Position(7 - to.row, to.col)
            } else {
                from to to
            }
        }

        val aiMoveIndex = state.moveHistory.count { it.pieceMoved.color == aiColor }
        if (aiMoveIndex < aiOpeningMoves.size) {
            val (from, to) = aiOpeningMoves[aiMoveIndex]
            if (isValidMove(from, to, state)) {
                return movePiece(from, to, state)
            } else {
                val piece = state.board[from]
                if (piece != null && piece.color == aiColor) {
                    val legal = getLegalMoves(from, state)
                    if (to in legal) return movePiece(from, to, state)
                }
            }
        }

        return null
    }

    private fun makeRandomMove(state: GameState): GameState? {
        val pieces = state.board.filter { it.value.color == state.turn }.keys.toList().shuffled()
        for (pos in pieces) {
            val moves = getLegalMoves(pos, state).shuffled()
            if (moves.isNotEmpty()) {
                var newState = movePiece(pos, moves.first(), state)
                if (newState.pendingPromotion != null) {
                    newState = promotePawn(newState.pendingPromotion!!, PieceType.QUEEN, newState)
                }
                return newState
            }
        }
        return null
    }

    private fun makeHeuristicMove(state: GameState, depth: Int): GameState {
        val moves = getAllLegalMoves(state)
        if (moves.isEmpty()) return state
        
        val aiColor = state.turn
        var bestVal = Int.MIN_VALUE
        var bestMoveState = moves.first()

        for (moveState in moves) {
            val value = minimax(moveState, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE, false, aiColor)
            if (value > bestVal) {
                bestVal = value
                bestMoveState = moveState
            }
        }
        
        if (bestMoveState.pendingPromotion != null) {
            bestMoveState = promotePawn(bestMoveState.pendingPromotion!!, PieceType.QUEEN, bestMoveState)
        }
        return bestMoveState
    }

    private fun minimax(state: GameState, depth: Int, alpha: Int, beta: Int, isMaximizing: Boolean, aiColor: PieceColor): Int {
        if (depth == 0 || isCheckmate(state) || isStalemate(state)) return evaluateBoard(state, aiColor)
        
        val moves = getAllLegalMoves(state)
        if (moves.isEmpty()) return evaluateBoard(state, aiColor)

        var currentAlpha = alpha
        var currentBeta = beta

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for (move in moves) {
                val eval = minimax(move, depth - 1, currentAlpha, currentBeta, false, aiColor)
                maxEval = max(maxEval, eval)
                currentAlpha = max(currentAlpha, eval)
                if (currentBeta <= currentAlpha) break
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            for (move in moves) {
                val eval = minimax(move, depth - 1, currentAlpha, currentBeta, true, aiColor)
                minEval = min(minEval, eval)
                currentBeta = min(currentBeta, eval)
                if (currentBeta <= currentAlpha) break
            }
            return minEval
        }
    }

    private fun getAllLegalMoves(state: GameState): List<GameState> {
        val result = mutableListOf<GameState>()
        val pieces = state.board.filter { it.value.color == state.turn }
        for ((pos, _) in pieces) {
            val moves = getLegalMoves(pos, state)
            for (to in moves) {
                result.add(movePiece(pos, to, state))
            }
        }
        return result
    }

    private fun evaluateBoard(state: GameState, perspectiveColor: PieceColor): Int {
        if (isCheckmate(state)) {
            return if (state.turn == perspectiveColor) -10000 else 10000
        }
        var score = 0
        
        // Bonus for castling (40 points)
        val hasCastled = state.moveHistory.any { it.pieceMoved.color == perspectiveColor && it.isCastling }
        if (hasCastled) score += 40
        
        // Penalty if opponent castled
        val opponentColor = if (perspectiveColor == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        val opponentCastled = state.moveHistory.any { it.pieceMoved.color == opponentColor && it.isCastling }
        if (opponentCastled) score -= 40

        for (piece in state.board.values) {
            val value = when (piece.type) {
                PieceType.PAWN -> 10
                PieceType.KNIGHT -> 30
                PieceType.BISHOP -> 30
                PieceType.ROOK -> 50
                PieceType.QUEEN -> 90
                PieceType.KING -> 900
            }
            if (piece.color == perspectiveColor) score += value else score -= value
        }
        return score
    }

    fun movePiece(from: Position, to: Position, state: GameState): GameState {
        val board = state.board.toMutableMap()
        val piece = board.remove(from)!!.copy(hasMoved = true)
        val captured = board.put(to, piece)
        
        var isCastling = false
        if (piece.type == PieceType.KING && abs(to.col - from.col) == 2) {
            isCastling = true
            val rookFromCol = if (to.col == 6) 7 else 0
            val rookToCol = if (to.col == 6) 5 else 3
            val rookPos = Position(from.row, rookFromCol)
            val rook = board.remove(rookPos)
            if (rook != null) {
                board[Position(from.row, rookToCol)] = rook.copy(hasMoved = true)
            }
        }

        var pendingPromotion: Position? = null
        if (piece.type == PieceType.PAWN && (to.row == 0 || to.row == 7)) {
            pendingPromotion = to
        }

        val move = Move(from, to, piece, captured, isCastling = isCastling)
        return state.copy(
            board = board,
            turn = if (pendingPromotion != null) state.turn else (if (state.turn == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE),
            moveHistory = state.moveHistory + move,
            pendingPromotion = pendingPromotion
        )
    }

    fun promotePawn(pos: Position, type: PieceType, state: GameState): GameState {
        val board = state.board.toMutableMap()
        val piece = board[pos] ?: return state
        board[pos] = piece.copy(type = type)
        return state.copy(
            board = board,
            turn = if (state.turn == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE,
            pendingPromotion = null
        )
    }

    fun isValidMove(from: Position, to: Position, state: GameState): Boolean {
        return to in getLegalMoves(from, state)
    }
}
