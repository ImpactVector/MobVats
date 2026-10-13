package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

public enum StageRequirement {
    ADULT,
    JUVENILE,
    ANY;

    public boolean accepts(LifeStage stage) {
        return this == ANY || name().equals(stage.name());
    }
}
