package net.impactvector.benchmark.mixin;

import net.impactvector.benchmark.Probes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(targets = "it.zerono.mods.zerocore.lib.multiblock.AbstractMultiblockController", remap = false)
public abstract class FrameworkProbe {
    @Inject(
            method = {"attachPart", "detachPart", "checkIfMachineIsWhole", "updateMultiblockEntity"},
            at = @At("HEAD"))
    private void enter(CallbackInfo ci) {
        Probes.enter();
    }

    @Inject(
            method = {"attachPart", "detachPart", "checkIfMachineIsWhole", "updateMultiblockEntity"},
            at = @At("RETURN"))
    private void exit(CallbackInfo ci) {
        Probes.exit(this, "ZeroCore." + ci.getId());
    }
}
