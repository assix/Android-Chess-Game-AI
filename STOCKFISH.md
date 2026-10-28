# Stockfish in MyApkChess (v0.8+)

MyApkChess uses **Stockfish 19** as its primary chess engine.

- **What ships in the APK:** the official `stockfish-android-arm64-universal`
  binary from the Stockfish `sf_19` release, bundled unmodified as
  `app/src/main/jniLibs/arm64-v8a/libstockfish.so` (97 MB) so Android
  extracts it to the app's executable native library directory. The app
  runs it as a separate process and talks to it over the UCI protocol
  (`StockfishEngine.kt`).
- **Rules fidelity:** positions are sent as FEN generated from the app's
  own board model. Castling rights come from the pieces' `hasMoved`
  flags; the en passant field is always `-` because this app's rules do
  not include en passant. Every move Stockfish returns is validated
  against the app's own legal-move generator before it is played.
- **Fallback:** if the Stockfish process cannot start or fails, the app
  silently uses its built-in Kotlin engine (`ChessLogic.kt`).
- **Difficulty mapping:** Easy = Skill Level 5, Medium = Skill Level 11,
  Hard = Skill Level 17, Expert = Skill Level 20 (Stockfish's UCI
  `Skill Level` option), with per-level move-time budgets.
- **Force AI to capture when possible:** implemented with UCI
  `searchmoves`, restricting Stockfish to the app's legal captures.

## License

Stockfish is free software licensed under the **GNU General Public
License v3** — see `STOCKFISH-COPYING.txt` in this repository.
Corresponding source for the bundled binary: the `sf_19` tag of the
official Stockfish repository,
<https://github.com/official-stockfish/Stockfish/tree/sf_19>
(the official release tarball also ships the full source tree).

Note: because a GPLv3 binary is bundled and distributed with the app,
the distributed APK as a whole is effectively covered by GPLv3 terms for
that component. The app's own Kotlin code remains under the repository
LICENSE unless/until the author states otherwise.
