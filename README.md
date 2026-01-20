# Android Chess Game AI

A fully functional chess application for Android featuring a custom-built AI engine using Minimax with Alpha-Beta pruning. This project demonstrates native Android development with complex logic processing on the main thread.

## 🧠 AI Engine
The opponent logic is built from scratch in Kotlin (`ChessLogic.kt`) and features three difficulty levels:

* **Algorithm:** **Minimax with Alpha-Beta Pruning**. This allows the AI to search the game tree efficiently by eliminating branches that do not influence the final decision.
* **Evaluation Function:** The board state is evaluated using a material-weight system (Pawn: 10, Knight/Bishop: 30, Rook: 50, Queen: 90, King: 900).
* **Difficulty Scaling:**
    * *Easy:* Random legal moves.
    * *Medium:* Depth-1 search (Greedy).
    * *Hard:* Depth-2 search with pruning for optimal short-term tactics.

## 📱 Features
* **Single Player Mode:** Play against the AI engine.
* **Legal Move Validation:** Real-time calculation of valid moves, preventing illegal plays and self-checks.
* **Game State Management:** Automatic detection of Check, Checkmate, and Stalemate conditions.
* **Clean UI:** Minimalist board design with adaptive icons and move highlighting.

## 🛠 Tech Stack
* **Language:** Kotlin
* **Architecture:** MVVM (Model-View-ViewModel) pattern
* **Framework:** Android SDK (Jetpack Compose / XML Views)
* **Tools:** Android Studio, Gemini (for code generation & optimization)

## 📦 Installation
1.  Clone the repo: 
    ```bash
    git clone https://github.com/assix/Android-Chess-Game-AI.git
    ```
2.  Open the project in **Android Studio**.
3.  Sync Gradle and run on an emulator or physical device.

## 🎮 Download & Play
Don't want to build from source? Download the latest playable version directly:
👉 [**Download Latest APK**](https://github.com/assix/Android-Chess-Game-AI/releases)

## 🤝 Contributing
Feel free to open issues or submit PRs if you want to improve the evaluation function or increase the search depth!
