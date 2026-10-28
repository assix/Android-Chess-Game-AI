# Android Chess Game AI

**Android Chess Game AI** is a high-performance, native Android chess application built with **Kotlin** and **Jetpack Compose**. Since v0.8 it is powered by **Stockfish 19**, the strongest open-source chess engine in the world, offering a fluid and challenging experience for players of all levels.

---

## 🆕 What's new in v0.8 — the Stockfish upgrade

* **New algorithm: Stockfish 19 replaces the home-grown minimax as the primary engine.** The previous custom Negamax engine had a root-search flaw that could make it misevaluate candidate moves — at Medium it sometimes skipped a free capture or left a piece hanging. v0.8 embeds the official Stockfish 19 binary (ARM64) and drives it over UCI: positions are translated to FEN under this app's exact ruleset (castling supported, no en passant), and every Stockfish reply is validated against the app's own legal-move generator before being played. Difficulty is now Stockfish's own Skill Level (Easy 5 · Medium 11 · Hard 17 · Expert 20). The built-in engine is still there as an automatic fallback — with the root-search flaw fixed — in case the Stockfish process can't run.
* **King of the Hill variant** (new toggle): win by checkmate or by moving your king onto d4, e4, d5 or e5. Pick the AI's stance, **Offensive** or **Defensive**. Stockfish doesn't natively know this variant, so a variant layer around the engine enforces it.
* **"Force AI to capture when possible"** toggle (renamed in v0.7) keeps working under Stockfish via UCI `searchmoves`.

---

## 🚀 Key Features

* **Stockfish 19 Engine**: Since v0.8 the opponent is the real **Stockfish 19** (bundled binary, UCI), with four difficulty levels mapped to Stockfish Skill Levels: Easy 5, Medium 11, Hard 17, Expert 20. The built-in Kotlin engine remains as an automatic fallback. See `STOCKFISH.md` for details and licensing.
* **Force AI to capture when possible**: Optional rule (Settings, off by default) that forces the AI to capture whenever a capture is available — "always eat when it can".
* **King of the Hill variant**: Optional variant (New Game / Settings, off by default): win by checkmate OR by legally moving your king onto one of the four central squares (d4, e4, d5, e5), which are highlighted gold. Choose the AI's approach — **Offensive** (it races its own king to the hill) or **Defensive** (it prioritises keeping your king away). Either way, the AI takes a hill win the moment one exists and never allows you an immediate hill win when it can be prevented. Official Stockfish doesn't know this variant, so `StockfishEngine.kt` enforces it around the engine (legal-move pool filtering + stance tie-breaking over Stockfish's MultiPV ranking); the built-in fallback engine has the same rules baked into its search.
* **Real-Time Opening Recognition**: Automatically identifies and displays classic chess openings as you play, such as the **London System**, **Sicilian Defense**, and **Ruy Lopez**.
* **Advanced Time Control**: Customizable player timers ranging from 3-minute blitz to 30-minute classical sessions.
* **Dynamic Help Mode**: Real-time visual indicators highlighting legal moves and identifying dangerous squares under immediate threat.
* **Strategic AI Openings**: Configure the AI to start with specific opening strategies to practice against particular lines.
* **Undo System**: Integrated move-rollback capability with a persistent counter to track your progress.
* **Tournament Standards**: Full support for complex rules including castling, pawn promotion (Queen, Rook, Bishop, Knight), and stalemate detection.

## 📸 Screenshots

<table style="width:100%">
  <tr>
    <th style="text-align:center">Gameplay</th>
  </tr>
  <tr>
    <td><img src="AndroidChessGameAI-screenshot.jpeg" width="300" alt="Gameplay"></td>
  </tr>
</table>

---

## 🧠 AI Logic & Algorithm

Since v0.8 the "brain" is **Stockfish 19**, the strongest open-source chess engine, bundled as a native binary and driven over UCI by `StockfishEngine.kt`. Positions are translated to FEN under this app's exact ruleset (castling yes, en passant no), and Stockfish's replies are validated against the app's own move generator before being played. Difficulty maps to Stockfish Skill Levels (5 / 11 / 17 / 20).

The original recursive decision-making engine below is still in the app as the built-in fallback (`ChessLogic.kt`), used automatically if the Stockfish process can't run.



### The Minimax Algorithm
The engine (v0.7) utilizes a **Negamax search** with **Alpha-Beta Pruning**, **MVV-LVA move ordering**, **quiescence search** over captures, **check extensions** (Expert) and **iterative deepening** under per-level time budgets, with mate-distance scoring so faster mates are preferred. Difficulty depths: Easy 1 ply, Medium 2, Hard 3, Expert 4 (+extensions). Easy is calibrated for beginners — real chess with human-like blunders — instead of the old uniformly random moves.

### Board Evaluation Heuristics
Each board position is assigned a numerical score based on several factors:
1.  **Material Value**: Standard piece weighting (Pawn=100, Knight=320, Bishop=330, Rook=500, Queen=900, King=20000 centipawns).
2.  **Piece-Square Tables**: Positional bonuses per piece, with a tapered king table that switches from castled safety in the middlegame to central activity in the endgame.
3.  **Structure & Safety**: Bishop pair, doubled/isolated/passed pawn terms, rooks on open files, and pawn-shield king safety.
4.  **Fast Attack Detection**: Checks and attacked squares are computed with direct pawn/knight/king/sliding-ray tests, which is what makes depth 3–4 search fast enough to run on a phone without freezing the UI (search runs on a background thread).

---

## 🛠️ Compilation & Deployment

### Local Environment (Ubuntu/Debian)
Ensure you have **Java 17** installed.
```bash
git clone [https://github.com/assix/Android-Chess-Game-AI.git](https://github.com/assix/Android-Chess-Game-AI.git)
cd Android-Chess-Game-AI
chmod +x gradlew
./gradlew assembleDebug
```
The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

### Google Colab (Cloud Build)
If you are away from your primary machine, you can compile the APK in the cloud:
1.  Open a new Colab notebook.
2.  Install OpenJDK 17 and set `JAVA_HOME`.
3.  Clone the repository, grant permissions to `gradlew`, and run `./gradlew assembleDebug`.
4.  Download the resulting file from `app/build/outputs/apk/debug/app-debug.apk`.

---
Developed by assix.
