# Android Chess Game AI (Chess Pro)

A native Android chess application built with Kotlin and Jetpack Compose, featuring a custom Minimax-based chess engine.

## Features
* **Play vs AI:** Custom engine with three difficulty levels (Easy, Medium, Hard).
* **Time Controls:** Play with 3, 5, 10, or 30-minute player timers.
* **Opening Recognition:** Real-time display of the current board opening (e.g., London System, Sicilian Defense).
* **AI Opening Strategy:** Choose the AI's preferred opening style before starting.
* **Help Mode:** Highlights legal moves and warns against dangerous squares in real-time.
* **Undo Tracking:** Undo moves with an active counter tracking usage during the game.
* **Full Chess Rules:** Strict move validation, castling, pawn promotion, checkmate, and stalemate detection.

## Setup and Compilation

This project uses Gradle and requires Java 17.

### Local Build (Ubuntu/Linux)
```bash
git clone [https://github.com/assix/Android-Chess-Game-AI.git](https://github.com/assix/Android-Chess-Game-AI.git)
cd Android-Chess-Game-AI
chmod +x gradlew
./gradlew assembleDebug
```
The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

### Google Colab Build
If you do not have a local Android SDK environment setup, you can compile the APK directly in Google Colab:

```python
# Install Java 17
!apt-get update -qq
!apt-get install openjdk-17-jdk-headless -qq > /dev/null
import os
os.environ["JAVA_HOME"] = "/usr/lib/jvm/java-17-openjdk-amd64"

# Clone and build
!git clone [https://github.com/assix/Android-Chess-Game-AI.git](https://github.com/assix/Android-Chess-Game-AI.git)
%cd Android-Chess-Game-AI
!chmod +x gradlew
!./gradlew assembleDebug
```

## Developer
Developed by assix.
