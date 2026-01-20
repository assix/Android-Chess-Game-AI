package com.example.myapkchess

enum class PieceType {
    PAWN, ROOK, KNIGHT, BISHOP, QUEEN, KING
}

enum class PieceColor {
    WHITE, BLACK
}

data class ChessPiece(
    val type: PieceType,
    val color: PieceColor
)

data class Position(val row: Int, val col: Int)

data class Move(val from: Position, val to: Position, val pieceMoved: ChessPiece, val pieceCaptured: ChessPiece? = null)

data class GameState(
    val board: Map<Position, ChessPiece> = initialBoard(),
    val turn: PieceColor = PieceColor.WHITE,
    val moveHistory: List<Move> = emptyList(),
    val difficulty: Int = 1,
    val isHelpMode: Boolean = false
)

fun initialBoard(): Map<Position, ChessPiece> {
    val board = mutableMapOf<Position, ChessPiece>()
    
    // Set up pieces
    for (col in 0..7) {
        board[Position(1, col)] = ChessPiece(PieceType.PAWN, PieceColor.BLACK)
        board[Position(6, col)] = ChessPiece(PieceType.PAWN, PieceColor.WHITE)
    }
    
    val backRow = listOf(
        PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
        PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
    )
    
    for (col in 0..7) {
        board[Position(0, col)] = ChessPiece(backRow[col], PieceColor.BLACK)
        board[Position(7, col)] = ChessPiece(backRow[col], PieceColor.WHITE)
    }
    
    return board
}
