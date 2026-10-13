import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitGeometry;
import net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferArt;

/** Development entry point: compiled gameplay values, never Java source text. */
public final class GeometryExport {
    public static void main(String[] args) throws Exception {
        var cosmetic = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var palettePath = Path.of(args[1]);
        var colors = JsonParser.parseString(Files.readString(palettePath)).getAsJsonObject().getAsJsonObject("fixed");
        var geometry = new JsonObject();
        // Emit integral model coordinates as integers, matching the existing resource contract.
        geometry.addProperty("nodeWidth", modelPixels(ConduitGeometry.DIAMETER));
        geometry.add("tapEndpointRatio", cosmetic.get("tapEndpointRatio"));
        geometry.add("cornerLength", cosmetic.get("cornerLength"));
        geometry.add("railWidth", cosmetic.get("railWidth"));
        String glass = argbColor(colors, "glass", palettePath);
        geometry.addProperty("glassColor", "#" + glass.substring(3));
        geometry.addProperty("glassAlpha", Integer.parseInt(glass.substring(1, 3), 16));
        geometry.addProperty("glassEdgeAlpha", Integer.parseInt(argbColor(colors, "glass_edge", palettePath).substring(1, 3), 16));
        geometry.addProperty("bufferWidth", modelPixels(FluidBufferArt.BODY.getXsize()));
        geometry.addProperty("bufferGlassAlpha", Integer.parseInt(argbColor(colors, "buffer_glass", palettePath).substring(1, 3), 16));
        var descriptor = new JsonObject();
        descriptor.addProperty("schemaVersion", 1);
        descriptor.addProperty("units", "model_pixels");
        descriptor.addProperty("modelPixelsPerBlock", 16);
        descriptor.add("geometry", geometry);
        var output = Path.of(args[2]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(descriptor) + "\n");
    }

    private static int modelPixels(double blocks) {
        double pixels = blocks * 16;
        if (pixels <= 0 || pixels != Math.rint(pixels))
            throw new IllegalArgumentException("Expected positive integral model-pixel geometry: " + blocks);
        return (int) pixels;
    }

    private static String argbColor(JsonObject colors, String key, Path source) {
        var value = colors == null ? null : colors.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !value.getAsString().matches("#[0-9a-fA-F]{8}"))
            throw new IllegalArgumentException(source + ": fixed." + key + ": expected #AARRGGBB color");
        return value.getAsString();
    }
}
