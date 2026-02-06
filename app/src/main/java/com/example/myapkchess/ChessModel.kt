package com.example.myapkchess

enum class PieceType {
    PAWN, ROOK, KNIGHT, BISHOP, QUEEN, KING
}

enum class PieceColor {
    WHITE, BLACK
}

data class ChessPiece(
    val type: PieceType,
    val color: PieceColor,
    val hasMoved: Boolean = false
)

data class Position(val row: Int, val col: Int)

data class Move(
    val from: Position,
    val to: Position,
    val pieceMoved: ChessPiece,
    val pieceCaptured: ChessPiece? = null,
    val isCastling: Boolean = false,
    val promotion: PieceType? = null
)

enum class OpeningType(val displayName: String) {
    RANDOM("Random"),
    LONDON("London System"),
    SICILIAN("Sicilian Defense"),
    KINGS_INDIAN("King's Indian"),
    FRENCH("French Defense"),
    RUI_LOPEZ("Ruy Lopez"),
    CARO_KANN("Caro-Kann"),
    ITALIAN("Italian Game"),
    SCANDINAVIAN("Scandinavian Defense"),
    QUEENS_GAMBIT("Queen's Gambit")
}

data class GameState(
    val board: Map<Position, ChessPiece> = initialBoard(),
    val turn: PieceColor = PieceColor.WHITE,
    val playerColor: PieceColor = PieceColor.WHITE,
    val moveHistory: List<Move> = emptyList(),
    val difficulty: Int = 2,
    val isHelpMode: Boolean = false,
    val selectedOpening: OpeningType = OpeningType.RANDOM,
    val pendingPromotion: Position? = null
)

fun initialBoard(): Map<Position, ChessPiece> {
    val board = mutableMapOf<Position, ChessPiece>()
    
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
