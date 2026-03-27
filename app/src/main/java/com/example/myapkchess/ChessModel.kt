package com.example.myapkchess

enum class PieceColor {
    WHITE, BLACK
}

enum class PieceType {
    PAWN, ROOK, KNIGHT, BISHOP, QUEEN, KING
}

data class Position(val row: Int, val col: Int)

data class ChessPiece(
    val type: PieceType,
    val color: PieceColor,
    val hasMoved: Boolean = false
)

data class Move(
    val from: Position,
    val to: Position,
    val pieceMoved: ChessPiece,
    val pieceCaptured: ChessPiece? = null,
    val isCastling: Boolean = false
)

enum class PlayStyle(val displayName: String) {
    RANDOM("Random"),
    DEFENSIVE("Defensive"),
    AGGRESSIVE("Aggressive")
}

enum class OpeningType(val displayName: String) {
    RANDOM("Random"),
    LONDON_SYSTEM("London System"),
    CARO_KANN("Caro-Kann"),
    SICILIAN_DEFENSE("Sicilian Defense"),
    KINGS_INDIAN("King's Indian")
}

data class GameState(
    val board: Map<Position, ChessPiece> = initialBoard(),
    val turn: PieceColor = PieceColor.WHITE,
    val moveHistory: List<Move> = emptyList(),
    val playerColor: PieceColor = PieceColor.WHITE,
    val difficulty: Int = 3,
    val isHelpMode: Boolean = false,
    val pendingPromotion: Position? = null,
    val selectedOpening: OpeningType = OpeningType.RANDOM,
    val playStyle: PlayStyle = PlayStyle.RANDOM
)

fun initialBoard(): Map<Position, ChessPiece> {
    val board = mutableMapOf<Position, ChessPiece>()
    
    // Pawns
    for (i in 0..7) {
        board[Position(1, i)] = ChessPiece(PieceType.PAWN, PieceColor.BLACK)
        board[Position(6, i)] = ChessPiece(PieceType.PAWN, PieceColor.WHITE)
    }

    // Rooks
    board[Position(0, 0)] = ChessPiece(PieceType.ROOK, PieceColor.BLACK)
    board[Position(0, 7)] = ChessPiece(PieceType.ROOK, PieceColor.BLACK)
    board[Position(7, 0)] = ChessPiece(PieceType.ROOK, PieceColor.WHITE)
    board[Position(7, 7)] = ChessPiece(PieceType.ROOK, PieceColor.WHITE)

    // Knights
    board[Position(0, 1)] = ChessPiece(PieceType.KNIGHT, PieceColor.BLACK)
    board[Position(0, 6)] = ChessPiece(PieceType.KNIGHT, PieceColor.BLACK)
    board[Position(7, 1)] = ChessPiece(PieceType.KNIGHT, PieceColor.WHITE)
    board[Position(7, 6)] = ChessPiece(PieceType.KNIGHT, PieceColor.WHITE)

    // Bishops
    board[Position(0, 2)] = ChessPiece(PieceType.BISHOP, PieceColor.BLACK)
    board[Position(0, 5)] = ChessPiece(PieceType.BISHOP, PieceColor.BLACK)
    board[Position(7, 2)] = ChessPiece(PieceType.BISHOP, PieceColor.WHITE)
    board[Position(7, 5)] = ChessPiece(PieceType.BISHOP, PieceColor.WHITE)

    // Queens
    board[Position(0, 3)] = ChessPiece(PieceType.QUEEN, PieceColor.BLACK)
    board[Position(7, 3)] = ChessPiece(PieceType.QUEEN, PieceColor.WHITE)

    // Kings
    board[Position(0, 4)] = ChessPiece(PieceType.KING, PieceColor.BLACK)
    board[Position(7, 4)] = ChessPiece(PieceType.KING, PieceColor.WHITE)

    return board
}