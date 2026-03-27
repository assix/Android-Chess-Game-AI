package com.example.myapkchess

import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
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
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("ChessPrefs", Context.MODE_PRIVATE) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var savedDifficulty by remember { mutableStateOf(sharedPref.getInt("diff", 3)) }
    var savedColorMode by remember { mutableStateOf(ColorSelectionMode.entries.getOrElse(sharedPref.getInt("color", 0)) { ColorSelectionMode.WHITE }) }
    var savedOpening by remember { mutableStateOf(OpeningType.entries.getOrElse(sharedPref.getInt("opening", 0)) { OpeningType.RANDOM }) }
    var savedPlayStyle by remember { mutableStateOf(PlayStyle.entries.getOrElse(sharedPref.getInt("style", 0)) { PlayStyle.RANDOM }) }
    var savedTimeMinutes by remember { mutableStateOf(sharedPref.getInt("time", 10)) }
    var lastPlayerColor by remember { mutableStateOf(PieceColor.BLACK) }
    
    // Stats tracking
    var totalGames by remember { mutableStateOf(sharedPref.getInt("totalGames", 0)) }
    var totalWins by remember { mutableStateOf(sharedPref.getInt("totalWins", 0)) }
    var totalLosses by remember { mutableStateOf(sharedPref.getInt("totalLosses", 0)) }
    var totalSeconds by remember { mutableStateOf(sharedPref.getLong("totalSeconds", 0L)) }
    var isOutcomeRecorded by remember { mutableStateOf(false) }

    var gameState by remember { mutableStateOf(GameState()) }
    var selectedPosition by remember { mutableStateOf<Position?>(null) }
    var showStartDialog by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var undoCount by remember { mutableStateOf(0) }

    var playerTimeSeconds by remember { mutableStateOf(600) }

    val isTimeOut = playerTimeSeconds <= 0
    val isGameOver = ChessLogic.isCheckmate(gameState) || ChessLogic.isStalemate(gameState) || isTimeOut

    // Handle Win/Loss tracking
    LaunchedEffect(isGameOver) {
        if (isGameOver && !isOutcomeRecorded && !showStartDialog) {
            if (isTimeOut) {
                totalLosses++
            } else if (ChessLogic.isCheckmate(gameState)) {
                if (gameState.turn == gameState.playerColor) {
                    totalLosses++ // Player got checkmated
                } else {
                    totalWins++ // AI got checkmated
                }
            }
            
            sharedPref.edit()
                .putInt("totalWins", totalWins)
                .putInt("totalLosses", totalLosses)
                .apply()
            
            isOutcomeRecorded = true
        }
    }

    if (showStartDialog) {
        StartDialog(
            initialDiff = savedDifficulty,
            initialColorMode = savedColorMode,
            initialOpening = savedOpening,
            initialPlayStyle = savedPlayStyle,
            initialTimeMinutes = savedTimeMinutes,
            onStart = { diff, colorMode, opening, style, timeMins ->
                savedDifficulty = diff
                savedColorMode = colorMode
                savedOpening = opening
                savedPlayStyle = style
                savedTimeMinutes = timeMins
                
                val playerColor = when (colorMode) {
                    ColorSelectionMode.WHITE -> PieceColor.WHITE
                    ColorSelectionMode.BLACK -> PieceColor.BLACK
                    ColorSelectionMode.RANDOM -> PieceColor.entries.random()
                    ColorSelectionMode.CYCLE -> if (lastPlayerColor == PieceColor.BLACK) PieceColor.WHITE else PieceColor.BLACK
                }
                lastPlayerColor = playerColor
                
                gameState = GameState(
                    difficulty = diff,
                    playerColor = playerColor,
                    selectedOpening = opening,
                    playStyle = style,
                    turn = PieceColor.WHITE 
                )
                
                playerTimeSeconds = timeMins * 60
                
                totalGames++
                isOutcomeRecorded = false
                sharedPref.edit()
                    .putInt("diff", diff)
                    .putInt("color", colorMode.ordinal)
                    .putInt("opening", opening.ordinal)
                    .putInt("style", style.ordinal)
                    .putInt("time", timeMins)
                    .putInt("totalGames", totalGames)
                    .apply()
                
                showStartDialog = false
                selectedPosition = null
                undoCount = 0
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            currentGameState = gameState,
            totalGames = totalGames,
            totalWins = totalWins,
            totalLosses = totalLosses,
            totalSeconds = totalSeconds,
            onDismiss = { showSettings = false },
            onUpdate = { gameState = it }
        )
    }

    LaunchedEffect(gameState.turn, isGameOver) {
        if (!isGameOver && !showStartDialog) {
            while (true) {
                delay(1000L)
                totalSeconds++
                if (totalSeconds % 5L == 0L) {
                    sharedPref.edit().putLong("totalSeconds", totalSeconds).apply()
                }
                
                if (gameState.turn == gameState.playerColor) {
                    if (playerTimeSeconds > 0) playerTimeSeconds--
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (!isLandscape) {
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
            }
        },
        bottomBar = {
            if (!isLandscape) {
                Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
                    BottomControls(
                        gameState = gameState, 
                        isTimeOut = isTimeOut, 
                        undoCount = undoCount, 
                        onReset = { showStartDialog = true },
                        onUndoClick = {
                            if (gameState.moveHistory.isNotEmpty() && !isTimeOut) {
                                var newState = undoMove(gameState)
                                if (newState.turn != gameState.playerColor && newState.moveHistory.isNotEmpty()) {
                                    newState = undoMove(newState)
                                }
                                gameState = newState
                                selectedPosition = null
                                undoCount++
                            }
                        },
                        onHelpToggle = {
                            gameState = gameState.copy(isHelpMode = it)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        GameStatusInfo(gameState, playerTimeSeconds, isGameOver, undoCount)
                        Spacer(modifier = Modifier.height(16.dp))
                        BottomControls(
                            gameState = gameState, 
                            isTimeOut = isTimeOut, 
                            undoCount = undoCount, 
                            onReset = { showStartDialog = true },
                            onUndoClick = {
                                if (gameState.moveHistory.isNotEmpty() && !isTimeOut) {
                                    var newState = undoMove(gameState)
                                    if (newState.turn != gameState.playerColor && newState.moveHistory.isNotEmpty()) {
                                        newState = undoMove(newState)
                                    }
                                    gameState = newState
                                    selectedPosition = null
                                    undoCount++
                                }
                            },
                            onHelpToggle = {
                                gameState = gameState.copy(isHelpMode = it)
                            }
                        )
                    }
                    Box(modifier = Modifier.weight(1.5f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        MainBoard(gameState, selectedPosition, isGameOver) { pos -> handleSquareClick(pos, gameState, selectedPosition) { gs, sp -> gameState = gs; selectedPosition = sp } }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GameStatusInfo(gameState, playerTimeSeconds, isGameOver, undoCount)
                    MainBoard(gameState, selectedPosition, isGameOver) { pos -> handleSquareClick(pos, gameState, selectedPosition) { gs, sp -> gameState = gs; selectedPosition = sp } }
                }
            }

            if (gameState.pendingPromotion != null) {
                PromotionDialog(
                    color = gameState.playerColor,
                    onSelect = { type ->
                        gameState = ChessLogic.promotePawn(gameState.pendingPromotion!!, type, gameState)
                    }
                )
            }
        }
    }

    LaunchedEffect(gameState.turn, gameState.playerColor, gameState.pendingPromotion, isGameOver) {
        if (gameState.turn != gameState.playerColor && !isGameOver && gameState.pendingPromotion == null) {
            delay(500)
            gameState = ChessLogic.getBestMove(gameState)
        }
    }
}

@Composable
fun GameStatusInfo(gameState: GameState, playerTimeSeconds: Int, isGameOver: Boolean, undoCount: Int) {
    val statusText = when {
        playerTimeSeconds <= 0 -> "Time's up! AI Wins."
        ChessLogic.isCheckmate(gameState) -> "Checkmate!"
        ChessLogic.isStalemate(gameState) -> "Stalemate!"
        gameState.turn == gameState.playerColor -> "Your Turn"
        else -> "AI Thinking..."
    }
    
    Text(
        text = statusText,
        style = MaterialTheme.typography.titleLarge,
        color = if (isGameOver) Color.Red else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    TimerView(
        timeSeconds = playerTimeSeconds,
        isTurn = gameState.turn == gameState.playerColor && !isGameOver,
        label = "You"
    )

    if (gameState.selectedOpening != OpeningType.RANDOM && gameState.moveHistory.size <= 12) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Opening: ${gameState.selectedOpening.displayName}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun BottomControls(gameState: GameState, isTimeOut: Boolean, undoCount: Int, onReset: () -> Unit, onUndoClick: () -> Unit, onHelpToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onUndoClick) { Text("Undo ($undoCount)") }
        
        Button(onClick = onReset) { Text("Reset") }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Help", fontSize = 12.sp)
            Switch(
                checked = gameState.isHelpMode,
                onCheckedChange = onHelpToggle
            )
        }
    }
}

fun handleSquareClick(pos: Position, gameState: GameState, selectedPosition: Position?, update: (GameState, Position?) -> Unit) {
    if (gameState.turn == gameState.playerColor && gameState.pendingPromotion == null) {
        val piece = gameState.board[pos]
        if (selectedPosition == null) {
            if (piece?.color == gameState.playerColor) update(gameState, pos)
        } else {
            if (ChessLogic.isValidMove(selectedPosition, pos, gameState)) {
                update(ChessLogic.movePiece(selectedPosition, pos, gameState), null)
            } else if (piece?.color == gameState.playerColor) {
                update(gameState, pos)
            } else {
                update(gameState, null)
            }
        }
    }
}

@Composable
fun MainBoard(gameState: GameState, selectedPosition: Position?, isGameOver: Boolean, onSquareClick: (Position) -> Unit) {
    val possibleMoves = if (selectedPosition != null && !isGameOver) {
        ChessLogic.getLegalMoves(selectedPosition, gameState)
    } else emptyList()

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.aspectRatio(1f).padding(4.dp)
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

                            if (piece != null) PieceIcon(piece = piece, fontSize = 36.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimerView(timeSeconds: Int, isTurn: Boolean, label: String) {
    val minutes = timeSeconds / 60
    val seconds = timeSeconds % 60
    val timeString = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    val bgColor = if (isTurn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isTurn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(vertical = 4.dp).width(120.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(text = "$label: $timeString", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (timeSeconds <= 10 && timeSeconds > 0) Color.Red else textColor)
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
                        PieceIcon(piece = ChessPiece(type, color), fontSize = 32.sp)
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
    initialDiff: Int, initialColorMode: ColorSelectionMode, initialOpening: OpeningType, initialPlayStyle: PlayStyle, initialTimeMinutes: Int,
    onStart: (Int, ColorSelectionMode, OpeningType, PlayStyle, Int) -> Unit
) {
    var diff by remember { mutableStateOf(initialDiff) }
    var colorMode by remember { mutableStateOf(initialColorMode) }
    var opening by remember { mutableStateOf(initialOpening) }
    var playStyle by remember { mutableStateOf(initialPlayStyle) }
    var timeMins by remember { mutableStateOf(initialTimeMinutes) }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.98f).padding(horizontal = 8.dp),
        title = { Text("New Game Setup") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Difficulty", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1, 2, 3).forEach { d ->
                        FilterChip(modifier = Modifier.weight(1f), selected = diff == d, onClick = { diff = d }, label = { Text(getDifficultyName(d), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) })
                    }
                }
                
                Text("AI Play Style", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlayStyle.entries.forEach { style ->
                        FilterChip(modifier = Modifier.weight(1f), selected = playStyle == style, onClick = { playStyle = style }, label = { Text(style.displayName, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) })
                    }
                }

                Text("Your Color", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ColorSelectionMode.entries.forEach { mode ->
                        FilterChip(modifier = Modifier.weight(1f), selected = colorMode == mode, onClick = { colorMode = mode }, label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth()) })
                    }
                }
                
                Text("Time Control", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(3, 5, 10, 30).forEach { t ->
                        FilterChip(modifier = Modifier.weight(1f), selected = timeMins == t, onClick = { timeMins = t }, label = { Text("${t}m", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) })
                    }
                }
                
                Text("AI Opening Strategy", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OpeningType.entries.forEach { o ->
                        FilterChip(selected = opening == o, onClick = { opening = o }, label = { Text(o.displayName, fontSize = 12.sp) })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onStart(diff, colorMode, opening, playStyle, timeMins) }) { Text("Start Game") } }
    )
}

@Composable
fun SettingsDialog(currentGameState: GameState, totalGames: Int, totalWins: Int, totalLosses: Int, totalSeconds: Long, onDismiss: () -> Unit, onUpdate: (GameState) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

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
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Player Stats", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Total Games Played: $totalGames", fontSize = 14.sp)
                Text("Won: $totalWins | Lost: $totalLosses", fontSize = 14.sp)
                Text("Total Time Played: ${hours}h${minutes}min", fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Developed by assix",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End).clickable { uriHandler.openUri("https://github.com/assix/Android-Chess-Game-AI") }.padding(8.dp)
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
    if (lastMove.pieceCaptured != null) newBoard[lastMove.to] = lastMove.pieceCaptured
    if (lastMove.isCastling) {
        val row = lastMove.from.row
        val rookFromCol = if (lastMove.to.col == 6) 7 else 0
        val rookToCol = if (lastMove.to.col == 6) 5 else 3
        val rook = newBoard.remove(Position(row, rookToCol))
        if (rook != null) newBoard[Position(row, rookFromCol)] = rook.copy(hasMoved = false)
    }
    return state.copy(board = newBoard, turn = lastMove.pieceMoved.color, moveHistory = state.moveHistory.dropLast(1), pendingPromotion = null)
}

fun getDifficultyName(level: Int) = when(level) { 1 -> "Easy"; 2 -> "Medium"; 3 -> "Hard"; else -> "Unknown" }

@Composable
fun PieceIcon(piece: ChessPiece, fontSize: TextUnit) {
    val symbol = when (piece.type) { PieceType.PAWN -> "♟"; PieceType.ROOK -> "♜"; PieceType.KNIGHT -> "♞"; PieceType.BISHOP -> "♝"; PieceType.QUEEN -> "♛"; PieceType.KING -> "♚" }
    Box(contentAlignment = Alignment.Center) {
        if (piece.color == PieceColor.WHITE) {
            Text(text = symbol, fontSize = fontSize, color = Color.Black, style = TextStyle(drawStyle = Stroke(width = 4f, join = StrokeJoin.Round)))
            Text(text = symbol, fontSize = fontSize, color = Color.White)
        } else {
            Text(text = symbol, fontSize = fontSize, color = Color.Black)
        }
    }
}