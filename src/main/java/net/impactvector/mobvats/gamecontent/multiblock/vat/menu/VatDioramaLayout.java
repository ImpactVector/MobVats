package net.impactvector.mobvats.gamecontent.multiblock.vat.menu;

/** Pixel-space composition for one fixed observation window. No world positions or species tables. */
public record VatDioramaLayout(
        float specimenX,
        float centerY,
        float specimenScale,
        float specimenWidth,
        float specimenHeight,
        float actorX,
        float actorScale) {
    public static final float BODY_YAW = 180;
    public static final float CAMERA_PITCH = 12;

    public static VatDioramaLayout fit(
            int width,
            int height,
            float bodyWidth,
            float bodyHeight,
            float adultWidth,
            float adultHeight,
            boolean actor) {
        if (width < 48
                || height < 32
                || !positive(bodyWidth)
                || !positive(bodyHeight)
                || !positive(adultWidth)
                || !positive(adultHeight)) throw new IllegalArgumentException("Invalid diorama bounds");
        float actorRegion = actor ? Math.min(70, width * .34f) : 0;
        float specimenRegion = width - actorRegion;
        // Conservative projected extents at the fixed yaw/pitch, including modest model overhang.
        float projectedWidth = bodyWidth * 1.65f;
        float projectedHeight = bodyHeight + bodyWidth * .45f;
        // Fit against adult dimensions as well: babies keep their smaller body and head proportions.
        float scale = Math.min(
                64,
                Math.min(
                        (specimenRegion - 16) / (Math.max(bodyWidth, adultWidth) * 1.65f),
                        (height - 24) / (Math.max(bodyHeight, adultHeight) + Math.max(bodyWidth, adultWidth) * .45f)));
        float actorScale = actor ? Math.min(30, Math.min((actorRegion - 8) / 1.8f, (height - 24) / 1.8f)) : 0;
        return new VatDioramaLayout(
                specimenRegion / 2,
                height / 2f + 3,
                scale,
                projectedWidth * scale,
                projectedHeight * scale,
                specimenRegion + actorRegion / 2,
                actorScale);
    }

    private static boolean positive(float value) {
        return Float.isFinite(value) && value > 0;
    }
}
