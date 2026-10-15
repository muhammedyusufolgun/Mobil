package tr.com.olgun.damar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VeinFilterTest {
    @Test public void outputRetainsSourceDimensions() {
        int[] input = new int[8 * 6];
        int[] output = VeinFilter.apply(input, 8, 6, 1.7f);
        assertEquals(8 * 6, output.length);
    }

    @Test public void outputIsGreenTintedAndClamped() {
        int[] input = new int[5 * 5];
        java.util.Arrays.fill(input, 0xffffffff);
        int pixel = VeinFilter.apply(input, 5, 5, 3f)[12];
        int r = (pixel >>> 16) & 0xff, g = (pixel >>> 8) & 0xff, b = pixel & 0xff;
        assertEquals(255, g);
        org.junit.Assert.assertTrue(g >= r);
        org.junit.Assert.assertTrue(g >= b);
    }

    @Test public void localDarkDetailGetsMoreContrast() {
        int[] input = new int[9];
        java.util.Arrays.fill(input, 0xffffffff);
        input[4] = 0xff646464;
        int[] output = VeinFilter.apply(input, 3, 3, 1.7f);
        int center = (output[4] >>> 8) & 0xff;
        int surrounding = (output[0] >>> 8) & 0xff;
        org.junit.Assert.assertTrue(center < surrounding);
    }
}
