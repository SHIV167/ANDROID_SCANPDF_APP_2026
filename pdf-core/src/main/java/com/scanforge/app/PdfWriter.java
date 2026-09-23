package com.scanforge.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** A streaming PDF 1.4 writer. Keeps only one JPEG in memory, even for 50-page scans. */
final class PdfWriter implements Closeable {
    private final RandomAccessFile output;
    private final long[] offsets;
    private final int count;
    private int written;
    private boolean finished;

    PdfWriter(File file, int count) throws IOException {
        if (count < 1 || count > 50) throw new IllegalArgumentException("A PDF must have 1–50 pages");
        this.count = count;
        offsets = new long[4 + count * 3];
        output = new RandomAccessFile(file, "rw"); output.setLength(0);
        write("%PDF-1.4\n%âãÏÓ\n");
        object(1, "<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < count; i++) kids.append(5 + i * 3).append(" 0 R ");
        object(2, "<< /Type /Pages /Count " + count + " /Kids [" + kids + "] >>");
        object(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
    }

    void addPage(byte[] jpeg, int imageWidth, int imageHeight, int width, int height, boolean numbers) throws IOException {
        if (finished || written >= count) throw new IllegalStateException("Unexpected PDF page");
        int image = 4 + written * 3, page = image + 1, content = image + 2;
        stream(image, "/Type /XObject /Subtype /Image /Width " + imageWidth + " /Height " + imageHeight +
            " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode", jpeg);
        object(page, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + width + " " + height +
            "] /Resources << /XObject << /Im0 " + image + " 0 R >> /Font << /F1 3 0 R >> >> /Contents " + content + " 0 R >>");
        float[] r = PdfLayout.fit(imageWidth, imageHeight, width, height, 24);
        String commands = String.format(Locale.ROOT, "q %.3f 0 0 %.3f %.3f %.3f cm /Im0 Do Q\n",
            r[2] - r[0], r[3] - r[1], r[0], height - r[3]);
        if (numbers) commands += "BT /F1 9 Tf 0.45 g " + (width / 2 - 10) + " 10 Td (" + (written + 1) + " / " + count + ") Tj ET\n";
        stream(content, "", commands.getBytes(StandardCharsets.ISO_8859_1)); written++;
    }

    void finish() throws IOException {
        if (written != count) throw new IOException("PDF is missing pages");
        if (finished) return;
        long xref = output.getFilePointer();
        write("xref\n0 " + offsets.length + "\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.length; i++) write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]));
        write("trailer\n<< /Size " + offsets.length + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        finished = true;
    }
    private void object(int id, String value) throws IOException { offsets[id] = output.getFilePointer(); write(id + " 0 obj\n" + value + "\nendobj\n"); }
    private void stream(int id, String dictionary, byte[] bytes) throws IOException {
        offsets[id] = output.getFilePointer(); write(id + " 0 obj\n<< " + dictionary + " /Length " + bytes.length + " >>\nstream\n");
        output.write(bytes); write("\nendstream\nendobj\n");
    }
    private void write(String text) throws IOException { output.write(text.getBytes(StandardCharsets.ISO_8859_1)); }
    @Override public void close() throws IOException { output.close(); }
}
