# Build verification — 23 September 2026

- Build: `:pdf-core:test lintDebug assembleDebug` — successful.
- Unit tests: 7 passed, 0 failed, 0 skipped.
- Tests include independent Apache PDFBox parsing/rendering of a two-page PDF, A4/Letter dimensions, page numbering, image color, margins, aspect ratios, valid cross-reference offsets, safe filenames, and incomplete-file rejection.
- Android lint: no errors. Dependency-version update advisories remain; library versions are pinned.
- APK: `dist/ScanForge-debug.apk`, also generated at `app/build/outputs/apk/debug/app-debug.apk`.
- APK signature: verified with Android SDK `apksigner`, v2 signing scheme, one debug signer.
- Package: `com.scanforge.app`, version 1.0.0, min SDK 26, target SDK 35.
- A SHA-256 digest accompanies the APK in `dist/ScanForge-debug.apk.sha256`.
- Build environment: Windows, Android SDK 35, Gradle 8.11.1, Android Gradle Plugin 8.9.2, JDK 21 compiling Java 17 source/bytecode.

No Android device was connected. Camera scanning, the first-use Google Play services download, OCR accuracy, UI rendering, accessibility, export-provider integration, and physical-device lifecycle behavior have not been verified on hardware. This is a debug-signed testing build, not a Play Store release. Follow `docs/DEVICE_TESTING.md` before production distribution.
