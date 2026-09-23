# Device acceptance checklist

Use a Google Play-enabled Android 8+ device with at least 1.7 GB RAM. These are manual acceptance checks, not a claim that device testing has already been performed.

1. First launch: empty library, Settings, defaults, large-font readability, and accessibility labels.
2. First scan online: allow scanner module download; capture two pages, adjust crop, apply cleanup, finish. Confirm two readable previews.
3. Cancel scanner and gallery picker: library remains unchanged.
4. Import gallery images inside scanner, including landscape photos. Confirm upright output and correct proportions.
5. Rotate phone during scanner preparation, image saving, OCR, and export. Confirm no permanent loading screen or duplicate save.
6. Rename, favorite, filter, sort, and reopen app. Confirm metadata and pages persist.
7. Add pages, rotate, enhance grayscale, reorder, remove a page; verify export follows edited page order. Re-extract OCR after edits.
8. OCR printed English text, then copy/share and search for a recognized word. Test a blank page and a blurry photo.
9. Merge a second document: appended pages appear and the source document remains available. Enforce 50-page limit.
10. Export A4 and Letter in each quality level. Open in two PDF viewers; confirm all pages, margins, aspect ratio, and optional numbering. Use a viewer's Print action.
11. Save with the system picker locally and to an installed cloud provider. Cancel save; confirm document remains unchanged. Simulate an unwritable provider.
12. Share PDF to another app and confirm the recipient can read the attachment.
13. Disable internet after scanner initialization. Confirm scanner availability and bundled OCR on that device. Test a device without Play services for an actionable error.
14. Delete a document only after confirmation. Cancel deletion and confirm it remains. Confirm externally saved copies survive deletion.
15. Try 50 pages, limited free storage, background/foreground transitions, process termination, and 200% font scaling. Verify failures do not destroy previously saved documents.

Before store distribution: choose the final application ID, provide branding/localization and a privacy policy, configure release signing, review current Play target API and SDK requirements, and complete the applicable store declarations and physical-device testing.
