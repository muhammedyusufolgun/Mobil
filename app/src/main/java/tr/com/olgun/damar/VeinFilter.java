package tr.com.olgun.damar;

import android.graphics.Bitmap;

/** A contrast and green-tone visualization filter, not a vein detector. */
public final class VeinFilter {
    static final int TONE_GREEN = 0;
    static final int TONE_BLUE = 1;
    static final int TONE_GRAY = 2;

    private VeinFilter() { }

    public static Bitmap enhance(Bitmap source, float strength) {
        return enhance(source, strength, TONE_GREEN);
    }

    public static Bitmap enhance(Bitmap source, float strength, int tone) {
        if (source == null) throw new IllegalArgumentException("source is required");
        int width = source.getWidth(), height = source.getHeight();
        int[] input = new int[width * height];
        source.getPixels(input, 0, width, 0, 0, width, height);
        return Bitmap.createBitmap(apply(input, width, height, strength, tone), width, height, Bitmap.Config.ARGB_8888);
    }

    /** Pure-Java kernel kept separate so the image transform can be tested without a device. */
    static int[] apply(int[] input, int width, int height, float strength) {
        return apply(input, width, height, strength, TONE_GREEN);
    }

    static int[] apply(int[] input, int width, int height, float strength, int tone) {
        if (input == null || input.length != width * height || width < 1 || height < 1)
            throw new IllegalArgumentException("pixel dimensions do not match");
        float amount = Math.max(0.5f, Math.min(4f, strength));
        float[] luminance = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            int c = input[i];
            luminance[i] = 0.26f * red(c) + 0.56f * green(c) + 0.18f * blue(c);
        }
        int[] output = new int[input.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sum = 0, count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= height) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx < 0 || xx >= width) continue;
                        sum += (int) luminance[yy * width + xx];
                        count++;
                    }
                }
                int i = y * width + x;
                float localMean = sum / (float) count;
                float enhanced = luminance[i] + amount * 1.15f * (luminance[i] - localMean);
                int value = clamp(Math.round(enhanced));
                output[i] = colorize(value, tone);
            }
        }
        return output;
    }

    private static int colorize(int value, int tone) {
        int r, g, b;
        if (tone == TONE_BLUE) {
            r = Math.round(value * 0.42f);
            g = Math.round(value * 0.68f);
            b = value;
        } else if (tone == TONE_GRAY) {
            r = value;
            g = value;
            b = value;
        } else {
            r = Math.round(value * 0.38f);
            g = value;
            b = Math.round(value * 0.55f);
        }
        return 0xff000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
    private static int red(int color) { return (color >>> 16) & 0xff; }
    private static int green(int color) { return (color >>> 8) & 0xff; }
    private static int blue(int color) { return color & 0xff; }
}
