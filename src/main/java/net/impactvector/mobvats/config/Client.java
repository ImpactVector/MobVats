package net.impactvector.mobvats.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client visual settings only; never used by machine validation or simulation. */
public final class Client {
    public final ModConfigSpec.IntValue fullDetailDistance;
    public final ModConfigSpec.IntValue reducedDetailDistance;
    public final ModConfigSpec.IntValue cullDistance;

    Client(ModConfigSpec.Builder builder) {
        builder.comment("Client-only vat interior render distances in blocks.",
            "Distances are measured from the camera to the nearest point on the full vat bounds.",
            "Out-of-order detail distances are normalized downward to respect cullDistance.").push("client");
        fullDetailDistance = builder.comment("Full specimen, head tracking and harness straps through this distance.")
            .defineInRange("fullDetailDistance", 32, 8, 512);
        reducedDetailDistance = builder.comment("Static specimen and cached basic straps through this distance.")
            .defineInRange("reducedDetailDistance", 64, 8, 512);
        cullDistance = builder.comment("Static specimen only through this distance; omit coordinated interior rendering farther away.")
            .defineInRange("cullDistance", 96, 8, 512);
        builder.pop();
    }
}
