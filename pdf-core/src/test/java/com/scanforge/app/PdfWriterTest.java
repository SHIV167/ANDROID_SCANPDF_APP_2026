package com.scanforge.app;

import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class PdfWriterTest {
    @Test public void exportedPagesOpenAndRenderInIndependentPdfEngine() throws Exception {
        File file = File.createTempFile("scanforge-render", ".pdf");
        try {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(100, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            graphics.setColor(java.awt.Color.RED); graphics.fillRect(0, 0, 100, 200); graphics.dispose();
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "jpg", jpeg);
            try (PdfWriter writer = new PdfWriter(file, 2)) {
                writer.addPage(jpeg.toByteArray(), 100, 200, 595, 842, true);
                writer.addPage(jpeg.toByteArray(), 100, 200, 612, 792, false);
                writer.finish();
            }
            try (org.apache.pdfbox.pdmodel.PDDocument pdf = org.apache.pdfbox.Loader.loadPDF(file)) {
                assertEquals(2, pdf.getNumberOfPages());
                assertEquals(595f, pdf.getPage(0).getMediaBox().getWidth(), .01f);
                assertEquals(792f, pdf.getPage(1).getMediaBox().getHeight(), .01f);
                assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).contains("1 / 2"));
                java.awt.image.BufferedImage rendered = new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImage(0);
                java.awt.Color center = new java.awt.Color(rendered.getRGB(297, 421));
                assertTrue(center.getRed() > 240 && center.getGreen() < 10);
                assertEquals(java.awt.Color.WHITE.getRGB(), rendered.getRGB(5, 5));
            }
        } finally { file.delete(); }
    }
    @Test public void crossReferencesPointToEveryObject() throws Exception {
        File file = File.createTempFile("scanforge", ".pdf");
        try {
            try (PdfWriter writer = new PdfWriter(file, 2)) {
                writer.addPage(new byte[]{(byte)255, (byte)216, (byte)255, (byte)217}, 100, 200, 595, 842, true);
                writer.addPage(new byte[]{(byte)255, (byte)216, (byte)255, (byte)217}, 200, 100, 612, 792, false);
                writer.finish();
            }
            String pdf = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
            assertTrue(pdf.startsWith("%PDF-1.4"));
            assertTrue(pdf.contains("/Count 2 /Kids [5 0 R 8 0 R ]"));
            int xref = Integer.parseInt(pdf.substring(pdf.lastIndexOf("startxref\n") + 10).split("\n")[0]);
            assertEquals("xref", pdf.substring(xref, xref + 4));
            String[] lines = pdf.substring(xref).split("\n");
            for (int id = 1; id < 10; id++) {
                int offset = Integer.parseInt(lines[id + 2].substring(0, 10));
                assertTrue(pdf.substring(offset).startsWith(id + " 0 obj\n"));
            }
            assertTrue(pdf.endsWith("%%EOF\n"));
        } finally { file.delete(); }
    }
    @Test(expected = IOException.class) public void incompletePdfCannotBeFinished() throws Exception {
        File file = File.createTempFile("scanforge", ".pdf");
        try (PdfWriter writer = new PdfWriter(file, 2)) { writer.finish(); }
        finally { file.delete(); }
    }
}
