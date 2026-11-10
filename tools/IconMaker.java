import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Turns the artwork in icon-source.png into Android launcher icons.
 *
 * <p>Run from the project root with a JDK 17:
 * {@code java tools/IconMaker.java icon-source.png}
 *
 * <p>What it does:
 * 1. makes the white/checkerboard outside the round badge transparent
 *    (flood fill from the border, so light pixels *inside* the artwork survive);
 * 2. crops to the artwork and centres it in a square;
 * 3. writes adaptive-icon foreground PNGs (108dp grid, artwork at 72%) plus
 *    legacy square icons and a 512px store icon.
 */
public final class IconMaker {

    private static final int[][] DENSITIES = {
            // density folder, legacy icon px, adaptive foreground px (108dp)
            {0, 48, 108},   // mdpi
            {1, 72, 162},   // hdpi
            {2, 96, 216},   // xhdpi
            {3, 144, 324},  // xxhdpi
            {4, 192, 432}   // xxxhdpi
    };
    private static final String[] FOLDERS = {
            "mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"
    };

    public static void main(String[] args) throws Exception {
        File source = new File(args.length > 0 ? args[0] : "icon-source.png");
        if (!source.isFile()) {
            System.err.println("Source image not found: " + source.getAbsolutePath());
            System.exit(2);
        }
        BufferedImage loaded = ImageIO.read(source);
        if (loaded == null) {
            System.err.println("Not a readable image: " + source);
            System.exit(2);
        }

        BufferedImage cut = stripBackground(loaded);
        BufferedImage square = squareCrop(cut);
        System.out.println("Source " + loaded.getWidth() + "x" + loaded.getHeight()
                + " -> artwork " + square.getWidth() + "x" + square.getHeight());

        File res = new File("app/src/main/res");
        for (int i = 0; i < FOLDERS.length; i++) {
            File dir = new File(res, FOLDERS[i]);
            dir.mkdirs();
            int legacy = DENSITIES[i][1];
            int foreground = DENSITIES[i][2];

            // Legacy launcher icons (used below API 26 and by some launchers).
            write(scale(square, legacy), new File(dir, "ic_launcher.png"));
            write(scale(square, legacy), new File(dir, "ic_launcher_round.png"));

            // Adaptive foreground: keep the badge inside the 66-72dp safe zone.
            BufferedImage canvas = new BufferedImage(foreground, foreground, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int content = Math.round(foreground * 0.72f);
            BufferedImage scaled = scale(square, content);
            g.drawImage(scaled, (foreground - content) / 2, (foreground - content) / 2, null);
            g.dispose();
            write(canvas, new File(dir, "ic_launcher_foreground.png"));
        }

        write(scale(square, 512), new File("app/src/main/res/ic_playstore_512.png"));
        System.out.println("Done. Point the adaptive icon at @mipmap/ic_launcher_foreground.");
    }

    /** Clears the outer background, including a baked-in transparency checkerboard. */
    private static BufferedImage stripBackground(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);

        boolean[] seen = new boolean[pixels.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int x = 0; x < width; x++) {
            push(queue, pixels, seen, x);
            push(queue, pixels, seen, (height - 1) * width + x);
        }
        for (int y = 0; y < height; y++) {
            push(queue, pixels, seen, y * width);
            push(queue, pixels, seen, y * width + width - 1);
        }

        int cleared = 0;
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            if (!isBackground(pixels[index])) continue;
            pixels[index] = 0x00000000;
            cleared++;
            int x = index % width;
            int y = index / width;
            if (x > 0) push(queue, pixels, seen, index - 1);
            if (x < width - 1) push(queue, pixels, seen, index + 1);
            if (y > 0) push(queue, pixels, seen, index - width);
            if (y < height - 1) push(queue, pixels, seen, index + width);
        }

        // JPEG noise keeps some checkerboard patches attached to the edge, so the
        // flood fill alone leaves crumbs. The badge is a circle: find it from the
        // pixels that are definitely artwork, then clear everything outside it.
        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[y * width + x];
                if (((argb >>> 24) & 0xFF) > 12 && isArtwork(argb)) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX > minX && maxY > minY) {
            double centerX = (minX + maxX) / 2.0;
            double centerY = (minY + maxY) / 2.0;

            // Callipers: walk many rays outwards to the dark disc edge. The
            // median radius describes the badge's circle exactly, which lets us
            // ignore the grey checkerboard crumbs that survive the flood fill.
            final int rays = 720;
            double[] hits = new double[rays];
            int hitCount = 0;
            final double maxSearch = Math.hypot(width, height);
            for (int a = 0; a < rays; a++) {
                double angle = 2 * Math.PI * a / rays;
                double dx = Math.cos(angle);
                double dy = Math.sin(angle);
                for (double t = maxSearch; t > 0; t -= 0.5) {
                    int x = (int) Math.round(centerX + dx * t);
                    int y = (int) Math.round(centerY + dy * t);
                    if (x < 0 || y < 0 || x >= width || y >= height) continue;
                    int argb = pixels[y * width + x];
                    if (((argb >>> 24) & 0xFF) > 12 && isArtwork(argb)) {
                        hits[hitCount++] = t;
                        break;
                    }
                }
            }
            double radius = Math.min(width, height) / 2.0;
            if (hitCount >= rays / 4) {
                double[] sorted = Arrays.copyOf(hits, hitCount);
                Arrays.sort(sorted);
                radius = sorted[hitCount / 2] * 1.008; // the thin cream rim rides just outside
            }

            int masked = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    int argb = pixels[index];
                    int alpha = (argb >>> 24) & 0xFF;
                    if (alpha == 0) continue;
                    double distance = Math.hypot(x + 0.5 - centerX, y + 0.5 - centerY);
                    if (distance <= radius) continue;
                    double feather = Math.min(1.0, distance - radius);
                    int newAlpha = (int) Math.round(alpha * (1.0 - feather));
                    pixels[index] = (newAlpha << 24) | (argb & 0x00FFFFFF);
                    masked++;
                }
            }
            // Dust-off: neutral grey crumbs that survive right at the rim. Only a
            // thin band is touched, and the peach rim itself is never grey.
            int crumbs = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    int argb = pixels[index];
                    if (((argb >>> 24) & 0xFF) == 0) continue;
                    double distance = Math.hypot(x + 0.5 - centerX, y + 0.5 - centerY);
                    if (distance < radius * 0.97 || distance > radius * 1.04) continue;
                    if (isGrey(argb)) {
                        pixels[index] = 0x00000000;
                        crumbs++;
                    }
                }
            }
            masked += crumbs;
            System.out.println("Badge circle r=" + Math.round(radius)
                    + ", masked " + masked + " px (" + crumbs + " rim crumbs)");
        }

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, width, height, pixels, 0, width);
        System.out.println("Cleared " + cleared + " background pixels");
        return result;
    }

    private static void push(ArrayDeque<Integer> queue, int[] pixels, boolean[] seen, int index) {
        if (index < 0 || index >= pixels.length || seen[index]) return;
        seen[index] = true;
        if (isBackground(pixels[index]) || isSemiTransparent(pixels[index])) queue.addLast(index);
    }

    private static boolean isSemiTransparent(int argb) {
        return ((argb >>> 24) & 0xFF) < 8;
    }

    /** White and light-grey (the drawn checkerboard) pixels qualify; warm cream and colour do not. */
    /** Neutral grey/white, the colour of a baked-in transparency checkerboard. */
    private static boolean isGrey(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return (max - min) <= 26 && max >= 150;
    }

    /** Dark disc or clearly coloured artwork — not the grey/white checkerboard. */
    private static boolean isArtwork(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return max < 170 || (max - min) > 50;
    }

    private static boolean isBackground(int argb) {
        if (isSemiTransparent(argb)) return false;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return (max - min) <= 18 && min >= 198;
    }

    private static BufferedImage squareCrop(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);

        int minX = width, minY = height, maxX = -1, maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((pixels[y * width + x] >>> 24) & 0xFF) > 12) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            minX = 0;
            minY = 0;
            maxX = width - 1;
            maxY = height - 1;
        }

        int side = Math.max(maxX - minX + 1, maxY - minY + 1);
        int left = minX - (side - (maxX - minX + 1)) / 2;
        int top = minY - (side - (maxY - minY + 1)) / 2;

        BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        g.drawImage(source, -left, -top, null);
        g.dispose();
        return square;
    }

    private static BufferedImage scale(BufferedImage source, int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return target;
    }

    private static void write(BufferedImage image, File file) throws Exception {
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
        System.out.println("wrote " + file.getPath() + " (" + image.getWidth() + "px)");
    }
}
