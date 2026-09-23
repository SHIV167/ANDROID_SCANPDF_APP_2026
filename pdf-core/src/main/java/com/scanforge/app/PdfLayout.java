package com.scanforge.app;

final class PdfLayout {
    // PDF coordinates are points, 72 points per inch. Preserve aspect ratio.
    static float[] fit(int imageWidth, int imageHeight, int pageWidth, int pageHeight, int margin) {
        if (imageWidth <= 0 || imageHeight <= 0 || margin < 0 ||
            pageWidth <= 2 * margin || pageHeight <= 2 * margin) {
            throw new IllegalArgumentException("Invalid page dimensions");
        }
        float scale = Math.min((pageWidth - margin * 2f) / imageWidth,
                              (pageHeight - margin * 2f) / imageHeight);
        float w = imageWidth * scale, h = imageHeight * scale;
        return new float[] {(pageWidth - w) / 2f, (pageHeight - h) / 2f,
                            (pageWidth + w) / 2f, (pageHeight + h) / 2f};
    }

    static String safeName(String title) {
        String name = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.isEmpty()) name = "Scan";
        return name.substring(0, Math.min(name.length(), 80));
    }
}
