package net.impactvector.mobvats.gamecontent.multiblock.vat;
/** Controller compiler orders compatible actors by descending priority, then stable position. */
public interface VatActor {
    /** Fluid-only actors do not claim neighboring item endpoints. */
    default boolean usesLocalItemPorts() {
        return false;
    }
    /** Fluid service connections belong to the actor; bulk storage belongs to physical Fluid Buffers. */
    default boolean usesLocalFluidPorts() {
        return true;
    }

    int priority();

    String useKey();

    default java.util.List<String> uses(MobAssembly assembly) {
        return java.util.List.of(useKey());
    }

    boolean compatible(MobAssembly assembly);

    default boolean reaches(
            net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos actor,
            net.minecraft.core.BlockPos min,
            net.minecraft.core.BlockPos max,
            MobAssembly target,
            java.util.List<MobAssembly> assemblies) {
        return VatTopologyCompiler.targets(level, actor, min, max, target, assemblies);
    }
}
