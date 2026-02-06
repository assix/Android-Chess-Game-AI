package com.example.myapkchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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

enum class ColorSelectionMode { WHITE, BLACK, RANDOM, CYCLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessApp() {
    // Persistent settings state
    var savedDifficulty by remember { mutableStateOf(3) } // Set to Hard by default
    var savedColorMode by remember { mutableStateOf(ColorSelectionMode.WHITE) }
    var savedOpening by remember { mutableStateOf(OpeningType.RANDOM) }
    var lastPlayerColor by remember { mutableStateOf(PieceColor.BLACK) } // For CYCLE mode

    var gameState by remember { mutableStateOf(GameState()) }
    var selectedPosition by remember { mutableStateOf<Position?>(null) }
    var showStartDialog by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }

    val isGameOver = ChessLogic.isCheckmate(gameState) || ChessLogic.isStalemate(gameState)

    if (showStartDialog) {
        StartDialog(
            initialDiff = savedDifficulty,
            initialColorMode = savedColorMode,
            initialOpening = savedOpening,
            onStart = { diff, colorMode, opening ->
                savedDifficulty = diff
                savedColorMode = colorMode
                savedOpening = opening
                
                val playerColor = when (colorMode) {
                    ColorSelectionMode.WHITE -> PieceColor.WHITE
                    ColorSelectionMode.BLACK -> PieceColor.BLACK
                    ColorSelectionMode.RANDOM -> PieceColor.values().random()
                    ColorSelectionMode.CYCLE -> if (lastPlayerColor == PieceColor.BLACK) PieceColor.WHITE else PieceColor.BLACK
                }
                lastPlayerColor = playerColor
                
                gameState = GameState(
                    difficulty = diff,
                    playerColor = playerColor,
                    selectedOpening = opening,
                    turn = PieceColor.WHITE 
                )
                showStartDialog = false
                selectedPosition = null
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            currentGameState = gameState,
            onDismiss = { showSettings = false },
            onUpdate = { gameState = it }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chess Pro", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        if (gameState.moveHistory.isNotEmpty()) {
                            var newState = undoMove(gameState)
                            if (newState.turn != gameState.playerColor && newState.moveHistory.isNotEmpty()) {
                                newState = undoMove(newState)
                            }
                            gameState = newState
                            selectedPosition = null
                        }
                    }) { Text("Undo") }
                    
                    Button(onClick = {
                        showStartDialog = true
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
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val statusText = when {
                    ChessLogic.isCheckmate(gameState) -> "CHECKMATE! ${if (gameState.turn == PieceColor.WHITE) "Black" else "White"} Wins!"
                    ChessLogic.isStalemate(gameState) -> "Stalemate!"
                    gameState.turn == gameState.playerColor -> "Your Turn"
                    else -> "AI Thinking..."
                }
                
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isGameOver) Color.Red else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                ChessBoard(
                    gameState = gameState,
                    selectedPosition = selectedPosition,
                    onSquareClick = { pos ->
                        if (gameState.turn == gameState.playerColor && !isGameOver && gameState.pendingPromotion == null) {
                            val piece = gameState.board[pos]
                            if (selectedPosition == null) {
                                if (piece?.color == gameState.playerColor) {
                                    selectedPosition = pos
                                }
                            } else {
                                if (ChessLogic.isValidMove(selectedPosition!!, pos, gameState)) {
                                    gameState = ChessLogic.movePiece(selectedPosition!!, pos, gameState)
                                    selectedPosition = null
                                } else if (piece?.color == gameState.playerColor) {
                                    selectedPosition = pos
                                } else {
                                    selectedPosition = null
                                }
                            }
                        }
                    }
                )
            }

            if (gameState.pendingPromotion != null) {
                PromotionDialog(
                    color = gameState.turn,
                    onSelect = { type ->
                        gameState = ChessLogic.promotePawn(gameState.pendingPromotion!!, type, gameState)
                    }
                )
            }
        }
    }

    // Key includes turn AND playerColor to fix the AI-not-starting-when-player-is-black bug
    LaunchedEffect(gameState.turn, gameState.playerColor) {
        if (gameState.turn != gameState.playerColor && !isGameOver && gameState.pendingPromotion == null) {
            delay(800)
            gameState = ChessLogic.getBestMove(gameState)
        }
    }
}

@Composable
fun PromotionDialog(color: PieceColor, onSelect: (PieceType) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Promote Pawn") },
        text = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { type ->
                    TextButton(onClick = { onSelect(type) }) {
                        Text(getPieceSymbol(ChessPiece(type, color)), fontSize = 32.sp)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartDialog(
    initialDiff: Int,
    initialColorMode: ColorSelectionMode,
    initialOpening: OpeningType,
    onStart: (Int, ColorSelectionMode, OpeningType) -> Unit
) {
    var diff by remember { mutableStateOf(initialDiff) }
    var colorMode by remember { mutableStateOf(initialColorMode) }
    var opening by remember { mutableStateOf(initialOpening) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("New Game Setup") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Difficulty", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1, 2, 3).forEach { d ->
                        FilterChip(selected = diff == d, onClick = { diff = d }, label = { Text(getDifficultyName(d)) })
                    }
                }
                
                Text("Your Color", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ColorSelectionMode.values().forEach { mode ->
                        FilterChip(
                            selected = colorMode == mode,
                            onClick = { colorMode = mode },
                            label = { Text(mode.name.lowercase().capitalize()) }
                        )
                    }
                }
                
                Text("AI Opening Strategy", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OpeningType.values().forEach { o ->
                        FilterChip(
                            selected = opening == o,
                            onClick = { opening = o },
                            label = { Text(o.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(diff, colorMode, opening) }) { Text("Start Game") }
        }
    )
}

@Composable
fun SettingsDialog(currentGameState: GameState, onDismiss: () -> Unit, onUpdate: (GameState) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game Settings") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Help Mode Indicators")
                    Switch(checked = currentGameState.isHelpMode, onCheckedChange = { onUpdate(currentGameState.copy(isHelpMode = it)) })
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Developed by assix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
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
    if (lastMove.isCastling) {
        val row = lastMove.from.row
        val rookFromCol = if (lastMove.to.col == 6) 7 else 0
        val rookToCol = if (lastMove.to.col == 6) 5 else 3
        val rook = newBoard.remove(Position(row, rookToCol))
        if (rook != null) {
            newBoard[Position(row, rookFromCol)] = rook.copy(hasMoved = false)
        }
    }
    return state.copy(
        board = newBoard,
        turn = lastMove.pieceMoved.color,
        moveHistory = state.moveHistory.dropLast(1),
        pendingPromotion = null
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
        modifier = Modifier.aspectRatio(1f).padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val range = if (gameState.playerColor == PieceColor.WHITE) 0..7 else 7 downTo 0
            for (row in range) {
                Row(modifier = Modifier.weight(1f)) {
                    val colRange = if (gameState.playerColor == PieceColor.WHITE) 0..7 else 7 downTo 0
                    for (col in colRange) {
                        val pos = Position(row, col)
                        val piece = gameState.board[pos]
                        val isSelected = pos == selectedPosition
                        val isPossibleMove = pos in possibleMoves
                        
                        val isLightSquare = (row + col) % 2 == 0
                        val bgColor = if (isLightSquare) Color(0xFFF0D9B5) else Color(0xFFB58863)
                        
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().background(bgColor).clickable { onSquareClick(pos) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) Box(modifier = Modifier.fillMaxSize().background(Color.Yellow.copy(alpha = 0.4f)))
                            
                            if (isPossibleMove) {
                                val indicatorColor = if (gameState.isHelpMode) {
                                    if (ChessLogic.isDangerous(pos, gameState.playerColor, gameState)) Color.Red.copy(alpha = 0.6f) else Color.Green.copy(alpha = 0.6f)
                                } else Color.Black.copy(alpha = 0.15f)
                                Box(modifier = Modifier.size(if(gameState.isHelpMode) 20.dp else 12.dp).background(indicatorColor, RoundedCornerShape(10.dp)))
                            }

                            if (piece != null) {
                                Text(
                                    text = getPieceSymbol(piece),
                                    fontSize = 40.sp,
                                    color = if (piece.color == PieceColor.WHITE) Color.White else Color.Black
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

fun String.capitalize() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
