package net.impactvector.benchmark.mixin;

import net.impactvector.benchmark.Probes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(
        targets = {
            "net.impactvector.mobvats.gamecontent.multiblock.vat.MultiblockVat",
            "net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer",
            "it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.MultiblockFluidizer",
            "it.zerono.mods.extremereactors.gamecontent.multiblock.reprocessor.MultiblockReprocessor",
            "it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.MultiblockReactor"
        },
        remap = false)
public abstract class MachineProbe {
    @Inject(
            method = {
                "onMachineAssembled",
                "onMachineRestored",
                "onMachinePaused",
                "onMachineDisassembled",
                "onPartAdded",
                "onPartRemoved"
            },
            at = @At("HEAD"))
    private void enter(CallbackInfo ci) {
        Probes.enter();
    }

    @Inject(
            method = {
                "onMachineAssembled",
                "onMachineRestored",
                "onMachinePaused",
                "onMachineDisassembled",
                "onPartAdded",
                "onPartRemoved"
            },
            at = @At("RETURN"))
    private void exit(CallbackInfo ci) {
        Probes.exit(this, ci.getId());
    }

    @Inject(method = "updateServer", at = @At("HEAD"))
    private void tickEnter(CallbackInfoReturnable<Boolean> ci) {
        Probes.enter();
    }

    @Inject(method = "updateServer", at = @At("RETURN"))
    private void tickExit(CallbackInfoReturnable<Boolean> ci) {
        Probes.exit(this, ci.getId());
    }
}
