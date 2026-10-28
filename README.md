# Android Chess Game AI

**Android Chess Game AI** is a high-performance, native Android chess application built with **Kotlin** and **Jetpack Compose**. It features a custom-built AI engine optimized for mobile devices, offering a fluid and challenging experience for players of all levels.

---

## 🚀 Key Features

* **Custom AI Engine**: Features a sophisticated Minimax-based opponent with four distinct difficulty levels: Easy, Medium, Hard, and Expert.
* **Force Capture Mode**: Optional rule (Settings, off by default) that forces the AI to capture whenever a capture is available — "always eat when it can".
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

The "brain" of Android Chess Game AI is a recursive decision-making engine designed for efficiency and tactical depth.



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
