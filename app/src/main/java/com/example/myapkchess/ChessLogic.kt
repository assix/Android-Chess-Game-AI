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

    fun isDangerous(position: Position, color: PieceColor, state: GameState): Boolean {
        val opponentColor = if (color == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        for ((pos, piece) in state.board) {
            if (piece.color == opponentColor) {
                if (isValidMoveBasic(pos, position, state = state.copy(turn = opponentColor))) return true
            }
        }
        return false
    }
    
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

    fun getBestMove(state: GameState): GameState {
        if (isCheckmate(state) || isStalemate(state)) return state
        
        val bookMove = getOpeningMove(state)
        if (bookMove != null) return bookMove
        
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
        val moves = getAllLegalMoves(state).shuffled()
        if (moves.isEmpty()) return state
        
        val isAiWhite = state.turn == PieceColor.WHITE
        var bestVal = if (isAiWhite) -1000000 else 1000000
        var bestMoveState = moves.first()

        for (moveState in moves) {
            val value = minimax(moveState, depth - 1, -1000000, 1000000, !isAiWhite)
            
            if (isAiWhite) {
                if (value > bestVal) {
                    bestVal = value
                    bestMoveState = moveState
                }
            } else {
                if (value < bestVal) {
                    bestVal = value
                    bestMoveState = moveState
                }
            }
        }
        return bestMoveState
    }

    private fun minimax(state: GameState, depth: Int, alpha: Int, beta: Int, isMaximizing: Boolean): Int {
        if (depth == 0 || isCheckmate(state) || isStalemate(state)) {
            return quiescenceSearch(state, alpha, beta, isMaximizing, 0)
        }
        
        val moves = getAllLegalMoves(state)
        if (moves.isEmpty()) return evaluateBoard(state)

        var currentAlpha = alpha
        var currentBeta = beta

        if (isMaximizing) {
            var maxEval = -1000000
            for (move in moves) {
                val eval = minimax(move, depth - 1, currentAlpha, currentBeta, false)
                maxEval = max(maxEval, eval)
                currentAlpha = max(currentAlpha, eval)
                if (currentBeta <= currentAlpha) break
            }
            return maxEval
        } else {
            var minEval = 1000000
            for (move in moves) {
                val eval = minimax(move, depth - 1, currentAlpha, currentBeta, true)
                minEval = min(minEval, eval)
                currentBeta = min(currentBeta, eval)
                if (currentBeta <= currentAlpha) break
            }
            return minEval
        }
    }

    private fun quiescenceSearch(state: GameState, alpha: Int, beta: Int, isMaximizing: Boolean, qsDepth: Int): Int {
        val standPat = evaluateBoard(state)
        if (qsDepth > 3 || isCheckmate(state) || isStalemate(state)) {
            return standPat
        }

        var currentAlpha = alpha
        var currentBeta = beta

        if (isMaximizing) {
            if (standPat >= currentBeta) return currentBeta
            if (standPat > currentAlpha) currentAlpha = standPat

            val captures = getAllCaptureMoves(state)
            for (moveState in captures) {
                val score = quiescenceSearch(moveState, currentAlpha, currentBeta, false, qsDepth + 1)
                if (score >= currentBeta) return currentBeta
                if (score > currentAlpha) currentAlpha = score
            }
            return currentAlpha
        } else {
            if (standPat <= currentAlpha) return currentAlpha
            if (standPat < currentBeta) currentBeta = standPat

            val captures = getAllCaptureMoves(state)
            for (moveState in captures) {
                val score = quiescenceSearch(moveState, currentAlpha, currentBeta, true, qsDepth + 1)
                if (score <= currentAlpha) return currentAlpha
                if (score < currentBeta) currentBeta = score
            }
            return currentBeta
        }
    }

    private fun getAllCaptureMoves(state: GameState): List<GameState> {
        val result = mutableListOf<GameState>()
        val opponentColor = if (state.turn == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        val pieces = state.board.filter { it.value.color == state.turn }
        for ((pos, _) in pieces) {
            val moves = getLegalMoves(pos, state)
            for (to in moves) {
                if (state.board[to]?.color == opponentColor) {
                    result.add(movePiece(pos, to, state))
                }
            }
        }
        return result
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
            return if (state.turn == PieceColor.WHITE) -100000 else 100000
        }
        if (isStalemate(state)) return 0

        var score = 0
        for ((pos, piece) in state.board) {
            var value = when (piece.type) {
                PieceType.PAWN -> 100
                PieceType.KNIGHT -> 300
                PieceType.BISHOP -> 300
                PieceType.ROOK -> 500
                PieceType.QUEEN -> 900
                PieceType.KING -> 9000
            }

            when (state.playStyle) {
                PlayStyle.AGGRESSIVE -> {
                    val forwardRank = if (piece.color == PieceColor.WHITE) (7 - pos.row) else pos.row
                    if (piece.type != PieceType.KING) value += forwardRank * 15
                }
                PlayStyle.DEFENSIVE -> {
                    val backRank = if (piece.color == PieceColor.WHITE) pos.row else (7 - pos.row)
                    if (piece.type != PieceType.KING) value += backRank * 10
                }
                PlayStyle.RANDOM -> {
                    value += (-10..10).random()
                }
            }

            if (piece.color == PieceColor.WHITE) {
                score += value
            } else {
                score -= value
            }
        }
        return score
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

    fun isValidMove(from: Position, to: Position, state: GameState): Boolean {
        return to in getLegalMoves(from, state)
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
}