package com.example.myapkchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapkchess.ui.theme.MyApkChessTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApkChessTheme {
                ChessApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessApp() {
    var gameState by remember { mutableStateOf(GameState()) }
    var selectedPosition by remember { mutableStateOf<Position?>(null) }
    var showDifficultyDialog by remember { mutableStateOf(true) }
    
    val isGameOver = ChessLogic.isCheckmate(gameState) || ChessLogic.isStalemate(gameState)

    if (showDifficultyDialog) {
        DifficultyDialog(onDifficultySelected = { diff ->
            gameState = gameState.copy(difficulty = diff)
            showDifficultyDialog = false
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chess AI", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.navigationBarsPadding() // Raises the bar above Android navigation
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp), // Increased vertical padding
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        if (gameState.moveHistory.isNotEmpty()) {
                            var newState = undoMove(gameState)
                            if (newState.turn == PieceColor.BLACK && newState.moveHistory.isNotEmpty()) {
                                newState = undoMove(newState)
                            }
                            gameState = newState
                            selectedPosition = null
                        }
                    }) { Text("Undo") }
                    
                    Button(onClick = {
                        gameState = GameState()
                        showDifficultyDialog = true
                        selectedPosition = null
                    }) { Text("Reset") }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Help", fontSize = 12.sp)
                        Switch(
                            checked = gameState.isHelpMode,
                            onCheckedChange = { gameState = gameState.copy(isHelpMode = it) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val statusText = when {
                ChessLogic.isCheckmate(gameState) -> "CHECKMATE! ${if (gameState.turn == PieceColor.WHITE) "Black" else "White"} Wins!"
                ChessLogic.isStalemate(gameState) -> "Stalemate! Draw."
                gameState.turn == PieceColor.WHITE -> "Your Turn"
                else -> "Computer is thinking..."
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineMedium,
                color = if (isGameOver) Color.Red else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            ChessBoard(
                gameState = gameState,
                selectedPosition = selectedPosition,
                onSquareClick = { pos ->
                    if (gameState.turn == PieceColor.WHITE && !isGameOver) {
                        val piece = gameState.board[pos]
                        if (selectedPosition == null) {
                            if (piece?.color == PieceColor.WHITE) {
                                selectedPosition = pos
                            }
                        } else {
                            if (ChessLogic.isValidMove(selectedPosition!!, pos, gameState)) {
                                gameState = ChessLogic.movePiece(selectedPosition!!, pos, gameState)
                                selectedPosition = null
                            } else if (piece?.color == PieceColor.WHITE) {
                                selectedPosition = pos
                            } else {
                                selectedPosition = null
                            }
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isGameOver) {
                Button(onClick = {
                    gameState = GameState()
                    showDifficultyDialog = true
                    selectedPosition = null
                }) {
                    Text("Play Again")
                }
            } else {
                Text(
                    text = "Difficulty: ${getDifficultyName(gameState.difficulty)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Computer Move Logic
    LaunchedEffect(gameState.turn) {
        if (gameState.turn == PieceColor.BLACK && !isGameOver) {
            delay(800)
            gameState = ChessLogic.getBestMove(gameState)
        }
    }
}

fun undoMove(state: GameState): GameState {
    if (state.moveHistory.isEmpty()) return state
    val lastMove = state.moveHistory.last()
    val newBoard = state.board.toMutableMap()
    newBoard.remove(lastMove.to)
    newBoard[lastMove.from] = lastMove.pieceMoved
    if (lastMove.pieceCaptured != null) {
        newBoard[lastMove.to] = lastMove.pieceCaptured
    }
    return state.copy(
        board = newBoard,
        turn = lastMove.pieceMoved.color,
        moveHistory = state.moveHistory.dropLast(1)
    )
}

fun getDifficultyName(level: Int) = when(level) {
    1 -> "Easy"
    2 -> "Medium"
    3 -> "Hard"
    else -> "Unknown"
}

@Composable
fun ChessBoard(
    gameState: GameState,
    selectedPosition: Position?,
    onSquareClick: (Position) -> Unit
) {
    val possibleMoves = if (selectedPosition != null) {
        ChessLogic.getLegalMoves(selectedPosition, gameState)
    } else emptyList()

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .aspectRatio(1f)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..7) {
                Row(modifier = Modifier.weight(1f)) {
                    for (col in 0..7) {
                        val pos = Position(row, col)
                        val piece = gameState.board[pos]
                        val isSelected = pos == selectedPosition
                        val isPossibleMove = pos in possibleMoves
                        
                        val isLightSquare = (row + col) % 2 == 0
                        val bgColor = if (isLightSquare) Color(0xFFF0D9B5) else Color(0xFFB58863)
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(bgColor)
                                .clickable { onSquareClick(pos) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Yellow.copy(alpha = 0.4f)))
                            }
                            
                            if (isPossibleMove) {
                                if (gameState.isHelpMode) {
                                    val isDangerous = ChessLogic.isDangerous(pos, PieceColor.WHITE, gameState)
                                    val indicatorColor = if (isDangerous) Color.Red.copy(alpha = 0.6f) else Color.Green.copy(alpha = 0.6f)
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(indicatorColor, RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    )
                                }
                            }

                            if (piece != null) {
                                Text(
                                    text = getPieceSymbol(piece),
                                    fontSize = 40.sp,
                                    color = if (piece.color == PieceColor.WHITE) Color.White else Color.Black,
                                    modifier = Modifier.offset(y = (-2).dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getPieceSymbol(piece: ChessPiece): String {
    return when (piece.color) {
        PieceColor.WHITE -> when (piece.type) {
            PieceType.PAWN -> "♙"
            PieceType.ROOK -> "♖"
            PieceType.KNIGHT -> "♘"
            PieceType.BISHOP -> "♗"
            PieceType.QUEEN -> "♕"
            PieceType.KING -> "♔"
        }
        PieceColor.BLACK -> when (piece.type) {
            PieceType.PAWN -> "♟"
            PieceType.ROOK -> "♜"
            PieceType.KNIGHT -> "♞"
            PieceType.BISHOP -> "♝"
            PieceType.QUEEN -> "♛"
            PieceType.KING -> "♚"
        }
    }
}

@Composable
fun DifficultyDialog(onDifficultySelected: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Choose Your Challenge") },
        text = {
            Column {
                Text("Select difficulty level:", modifier = Modifier.padding(bottom = 16.dp))
                Button(
                    onClick = { onDifficultySelected(1) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) { Text("Easy (Random moves)") }
                Button(
                    onClick = { onDifficultySelected(2) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Medium (Thinking ahead)") }
                Button(
                    onClick = { onDifficultySelected(3) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) { Text("Hard (Chess Master)") }
            }
        },
        confirmButton = {}
    )
}
