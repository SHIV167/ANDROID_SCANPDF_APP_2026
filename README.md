# ScanForge — Android document scanner

A native Android app that turns camera scans and gallery images into multi-page PDFs. Built in Java 17, with AndroidX and Google ML Kit. Android 8.0+.

The locally built test APK is available at `dist/ScanForge-debug.apk` (about 45 MB). Transfer it to an Android phone and open it to install. The build, seven PDF tests, and Android lint completed successfully; physical-device acceptance testing is still pending. See [build verification](docs/BUILD_VERIFICATION.md).

## Included

- Google ML Kit full scanner: camera capture, gallery import, automatic edge detection, crop/perspective correction, rotation, filters, and cleanup in the scanner flow.
- Up to 50 pages per document. Add more scans, reorder pages, rotate, enhance grayscale, or remove pages.
- Local document library with rename, favorites, sorting, title search, and extracted-text search.
- Merge another document's pages without deleting the source.
- On-device Latin-script OCR. Select, copy, or share extracted text.
- Streaming JPEG-backed PDF export: A4/US Letter, three resolution levels, optional page numbers, preserved aspect ratio.
- Save with Android's system file picker (including installed cloud document providers), share with URI grants, or open in a PDF viewer for printing.
- Atomic metadata persistence, private app storage, background processing, rotation-safe operation state, and confirmation before deleting documents/pages.
- Green/cream interface, real empty states, page previews, and settings/privacy information.

## Build

Open this folder in Android Studio. Install Android SDK Platform 35 and Build Tools 35.0.0. Use JDK 17 or 21.

```powershell
# Set this to your JDK installation if java is not already on PATH.
$env:JAVA_HOME = 'E:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :pdf-core:test lintDebug assembleDebug
```

On macOS/Linux: `chmod +x gradlew && ./gradlew :pdf-core:test lintDebug assembleDebug`.

Set `sdk.dir` in an untracked `local.properties` file if Android Studio has not already done so. Build output: `app/build/outputs/apk/debug/app-debug.apk`.

Connect an Android phone with USB debugging enabled and run `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or transfer the APK to your phone and open it to install. The debug APK is for testing. Configure your own private release signing key for distribution; no production signing keys are included.

## Device requirements and boundaries

- Scanning needs Google Play services, at least 1.7 GB device RAM, and internet access for its initial module download. The SDK owns the camera permission flow; this app does not request broad storage or camera permissions.
- OCR is bundled and supports Latin-script text. OCR text is stored separately; exported PDFs are image-based, without a searchable OCR text layer.
- PDF printing is available through an installed viewer's Print action. This app does not include a separate print renderer.
- No account, app-managed cloud sync, ads, subscriptions, analytics, PDF password encryption, digital signatures, or PDF-file import. Gallery image import is supported inside the scanner.
- Files are private to the app, but not additionally encrypted by an app password. Android backup is disabled. Uninstalling removes the library; export important documents first.
- Cropping/cleanup is offered during capture/import. Saved-page editing supports rotation, grayscale enhancement, reordering, and removal; rescan to change the crop.
- Configuration changes preserve ongoing jobs through a ViewModel. Saved documents survive process termination; an interrupted unfinished operation must be retried. Retired edit files are kept until the whole document is deleted. Shared export files reside in Android's disposable cache.

## Verification

`:pdf-core:test` exercises PDF aspect-ratio layout, filename sanitization, cross-reference offsets, incomplete-PDF rejection, and rendering using the independent Apache PDFBox engine. The pure Java PDF module keeps desktop test dependencies out of the Android app. `lintDebug` checks Android API and resource usage. GitHub Actions builds a debug APK and runs these checks on pushes and pull requests.

Real-camera capture, Google Play services module download, OCR accuracy, sharing targets, screen-reader navigation, and manufacturer-specific behavior require phone testing. See [device acceptance checklist](docs/DEVICE_TESTING.md).

## Structure

- `MainActivity.java`: library, scanner integration, page editor, OCR, settings, export/share.
- `ScanSession.java`: retained operation state and serialized background work.
- `DocumentStore.java`: private page storage, atomic index, transforms, PDF orchestration.
- `PdfWriter.java` / `PdfLayout.java`: bounded-memory PDF serialization and page geometry.
- `Document.java`: explicit JSON model serialization.

API references: [ML Kit document scanner](https://developers.google.com/ml-kit/vision/doc-scanner/android), [ML Kit text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).
