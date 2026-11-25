package net.impactvector.mobvats.gamecontent.journal;

import static net.impactvector.mobvats.gamecontent.journal.JournalData.*;

import java.util.*;
import java.util.function.Function;

/** Pure CPU ARGB compositor. NativeImage conversion belongs to the client adapter. */
public final class JournalPageCompositor {
    public static final int WIDTH = 256, HEIGHT = 332;

    public record Pixels(int width, int height, int[] argb) {
        public Pixels {
            if (width < 1 || height < 1 || argb.length != width * height) throw new IllegalArgumentException("Pixels");
        }
    }

    public enum Layer {
        ALL,
        PAPER,
        SURFACE,
        PHYSICAL,
        AUTHORED
    }

    public static Pixels missingTexture(boolean paper) {
        return new Pixels(1, 1, new int[] {paper ? 0xffe5d9b4 : 0});
    }

    public static boolean protectedContent(int x, int y) {
        return x >= 12 && x < 244 && y >= 18 && y < 310;
    }

    public static Pixels compose(
            JournalDecorationGenerator.Layout layout,
            Map<String, Asset> assets,
            Function<String, Pixels> textures,
            Layer layer) {
        Pixels paper = textures.apply(assets.get(layout.paper()).texture());
        int[] result = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++)
                result[y * WIDTH + x] =
                        paper.argb()[(y * paper.height() / HEIGHT) * paper.width() + x * paper.width() / WIDTH];
        if (layer == Layer.ALL || layer == Layer.PHYSICAL) apply(result, layout.physical(), assets, textures, false);
        if (layer == Layer.ALL || layer == Layer.SURFACE) apply(result, layout.surface(), assets, textures, false);
        if (layer == Layer.ALL || layer == Layer.AUTHORED) apply(result, layout.authored(), assets, textures, false);
        // Preserve the base paper silhouette too: alpha-over must not grow paper into transparent texels.
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++) {
                int base = paper.argb()[(y * paper.height() / HEIGHT) * paper.width() + x * paper.width() / WIDTH];
                int index = y * WIDTH + x;
                result[index] = (result[index] & 0xffffff) | (base & 0xff000000);
            }
        // Masks are always last, so neither generated nor authored stains can bridge absent paper.
        if (layer == Layer.ALL || layer == Layer.PHYSICAL) apply(result, layout.physical(), assets, textures, true);
        return new Pixels(WIDTH, HEIGHT, result);
    }

    private static void apply(
            int[] out,
            List<Decoration> decorations,
            Map<String, Asset> assets,
            Function<String, Pixels> textures,
            boolean masks) {
        for (var d : decorations) {
            var asset = assets.get(d.asset());
            if (asset.category().equals("masks") != masks) continue;
            var image = textures.apply(asset.texture());
            double angle = Math.toRadians(d.rotation()), cos = Math.cos(angle), sin = Math.sin(angle);
            for (int y = 0; y < HEIGHT; y++)
                for (int x = 0; x < WIDTH; x++) {
                    if (masks && protectedContent(x, y)) continue;
                    double dx = x + 0.5 - d.x() * WIDTH, dy = y + 0.5 - d.y() * HEIGHT;
                    double u = (cos * dx + sin * dy) / d.scale(), v = (-sin * dx + cos * dy) / d.scale();
                    if (d.mirror()) u = -u;
                    int sx = (int) Math.floor(u + image.width() / 2.0), sy = (int) Math.floor(v + image.height() / 2.0);
                    if (sx < 0 || sy < 0 || sx >= image.width() || sy >= image.height()) continue;
                    int source = image.argb()[sy * image.width() + sx], index = y * WIDTH + x;
                    double alpha = (source >>> 24) / 255.0 * d.opacity();
                    // Stains in the technical content area remain deliberately faint.
                    if (!masks && protectedContent(x, y)) alpha *= 0.18;
                    if (masks) out[index] = (out[index] & 0xffffff) | ((int) ((out[index] >>> 24) * (1 - alpha)) << 24);
                    else out[index] = over(out[index], source, alpha);
                }
        }
    }

    private static int over(int dest, int source, double alpha) {
        double da = (dest >>> 24) / 255.0, oa = alpha + da * (1 - alpha);
        if (oa == 0) return 0;
        int r = (int) ((((source >> 16) & 255) * alpha + ((dest >> 16) & 255) * da * (1 - alpha)) / oa);
        int g = (int) ((((source >> 8) & 255) * alpha + ((dest >> 8) & 255) * da * (1 - alpha)) / oa);
        int b = (int) (((source & 255) * alpha + (dest & 255) * da * (1 - alpha)) / oa);
        return ((int) (oa * 255) << 24) | (r << 16) | (g << 8) | b;
    }
}
