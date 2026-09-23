package com.scanforge.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class PdfLayoutTest {
    @Test public void portraitPageFitsWithinA4Margins() {
        float[] r = PdfLayout.fit(1200, 2400, 595, 842, 24);
        assertEquals(24, r[1], .01);
        assertEquals(818, r[3], .01);
        assertEquals(0.5, (r[2] - r[0]) / (r[3] - r[1]), .001);
        assertEquals(595, r[0] + r[2], .01);
    }
    @Test public void landscapeImageFitsLetterWithoutStretching() {
        float[] r = PdfLayout.fit(2400, 1200, 612, 792, 24);
        assertEquals(24, r[0], .01);
        assertEquals(588, r[2], .01);
        assertEquals(2, (r[2] - r[0]) / (r[3] - r[1]), .001);
    }
    @Test(expected = IllegalArgumentException.class) public void invalidImageRejected() {
        PdfLayout.fit(0, 10, 595, 842, 24);
    }
    @Test public void exportNameCannotEscapeDirectory() {
        assertEquals(".._.._invoice_2026", PdfLayout.safeName("../../invoice:2026"));
        assertEquals("Scan", PdfLayout.safeName("  "));
        assertEquals(80, PdfLayout.safeName("a".repeat(100)).length());
    }
}
