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
                    // Filter moves that leave our king in check
                    if (!moveLeavesKingInCheck(position, target, state)) {
                        candidateMoves.add(target)
                    }
                }
            }
        }
        return candidateMoves
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

    private fun isKingInCheck(color: PieceColor, board: Map<Position, ChessPiece>): Boolean {
        val kingPos = board.entries.find { it.value.type == PieceType.KING && it.value.color == color }?.key ?: return false
        val opponentColor = if (color == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        
        for ((pos, piece) in board) {
            if (piece.color == opponentColor) {
                if (isValidMoveBasic(pos, kingPos, state = GameState(board = board, turn = opponentColor))) {
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
        val opponentColor = if (color == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        for ((pos, piece) in state.board) {
            if (piece.color == opponentColor) {
                if (isValidMoveBasic(pos, position, state = state.copy(turn = opponentColor))) return true
            }
        }
        return false
    }

    fun getBestMove(state: GameState): GameState {
        if (isCheckmate(state) || isStalemate(state)) return state
        
        return when (state.difficulty) {
            1 -> makeRandomMove(state) ?: state
            2 -> makeHeuristicMove(state, depth = 1)
            3 -> makeHeuristicMove(state, depth = 2)
            else -> makeRandomMove(state) ?: state
        }
    }

    private fun makeRandomMove(state: GameState): GameState? {
        val pieces = state.board.filter { it.value.color == state.turn }.keys.toList().shuffled()
        for (pos in pieces) {
            val moves = getLegalMoves(pos, state).shuffled()
            if (moves.isNotEmpty()) {
                return movePiece(pos, moves.first(), state)
            }
        }
        return null
    }

    private fun makeHeuristicMove(state: GameState, depth: Int): GameState {
        // Shuffle the moves so opening ties are evaluated and picked randomly
        val moves = getAllLegalMoves(state).shuffled()
        if (moves.isEmpty()) return state
        
        var bestVal = Int.MIN_VALUE
        var bestMoveState = moves.first()

        for (moveState in moves) {
            val value = minimax(moveState, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE, false)
            if (value > bestVal) {
                bestVal = value
                bestMoveState = moveState
            }
        }
        return bestMoveState
    }

    private fun minimax(state: GameState, depth: Int, alpha: Int, beta: Int, isMaximizing: Boolean): Int {
        if (depth == 0 || isCheckmate(state) || isStalemate(state)) return evaluateBoard(state)
        
        val moves = getAllLegalMoves(state)
        if (moves.isEmpty()) return evaluateBoard(state)

        var currentAlpha = alpha
        var currentBeta = beta

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for (move in moves) {
                val eval = minimax(move, depth - 1, currentAlpha, currentBeta, false)
                maxEval = max(maxEval, eval)
                currentAlpha = max(currentAlpha, eval)
                if (currentBeta <= currentAlpha) break
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            for (move in moves) {
                val eval = minimax(move, depth - 1, currentAlpha, currentBeta, true)
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

    private fun evaluateBoard(state: GameState): Int {
        if (isCheckmate(state)) {
            return if (state.turn == PieceColor.WHITE) 10000 else -10000
        }
        var score = 0
        for (piece in state.board.values) {
            val value = when (piece.type) {
                PieceType.PAWN -> 10
                PieceType.KNIGHT -> 30
                PieceType.BISHOP -> 30
                PieceType.ROOK -> 50
                PieceType.QUEEN -> 90
                PieceType.KING -> 900
            }
            if (piece.color == PieceColor.BLACK) score += value else score -= value
        }
        return score
    }

    fun movePiece(from: Position, to: Position, state: GameState): GameState {
        val board = state.board.toMutableMap()
        val piece = board.remove(from)!!
        val captured = board.put(to, piece)
        val move = Move(from, to, piece, captured)
        return state.copy(
            board = board,
            turn = if (state.turn == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE,
            moveHistory = state.moveHistory + move
        )
    }

    fun isValidMove(from: Position, to: Position, state: GameState): Boolean {
        return to in getLegalMoves(from, state)
    }
    fun promotePawn(position: Position, newType: PieceType, state: GameState): GameState {
        val board = state.board.toMutableMap()
        val piece = board[position]
        if (piece != null) {
            board[position] = ChessPiece(newType, piece.color)
        }
        return state.copy(
            board = board,
            pendingPromotion = null
        )
    }
}
