package net.impactvector.mobvats.gametest;

import net.impactvector.mobvats.gamecontent.creative.*;
import net.impactvector.mobvats.gamecontent.multiblock.common.SyncDiagnostics;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.*;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.part.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import it.zerono.mods.zerocore.lib.data.nbt.ISyncableEntity.SyncReason;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("mobvats") @PrefixGameTestTemplate(false)
public final class SyncArchitectureGameTests {
    private static int nbtBytes(CompoundTag data){
        try{var bytes=new java.io.ByteArrayOutputStream();net.minecraft.nbt.NbtIo.write(data,new java.io.DataOutputStream(bytes));return bytes.size();}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static final BlockPos CONTROLLER=new BlockPos(1,2,3),DRIVE=new BlockPos(4,4,3);
    @GameTest(template="empty",timeoutTicks=60)
    public static void mixerDiagnosticScenarios(GameTestHelper h){
        MixerGameTests.rig(h);h.setBlock(DRIVE,CreativeContent.block(CreativeDeviceType.DRIVE));
        h.runAtTickTime(35,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(CONTROLLER)).getMultiblockController().orElseThrow();
            h.assertTrue(c.isAssembled(),"Diagnostic fixture unassembled");
            var saved=c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);
            c.syncDataFrom(saved,h.getLevel().registryAccess(),SyncReason.FullSync);
            try(var d=SyncDiagnostics.capture(c)){
                MixerCombustionGameTests.ticks(c,c.spinUpTicks());
                h.assertTrue(c.operatingState()==MixerOperatingState.RUNNING,"Spin-up mechanics changed");
                h.assertTrue(d.count("MultiblockMixer.referenceUpdate")==1+c.spinUpTicks()/20,"Spin-up did not publish transitions/corrections at expected cadence");
                com.mojang.logging.LogUtils.getLogger().info("Slice3 spin-up ticks={} counts={}",c.spinUpTicks(),d.snapshot());
            }
            MixerCombustionGameTests.ticks(c,20); // First blocked-status transition is meaningful.
            try(var d=SyncDiagnostics.capture(c)){
                MixerCombustionGameTests.ticks(c,1000);
                h.assertTrue(d.count("MultiblockMixer.dirty")==0 && d.count("MultiblockMixer.referenceUpdate")==0,"Unchanged running state requests updates");
                com.mojang.logging.LogUtils.getLogger().info("Slice3 running-blocked 1000 ticks counts={}",d.snapshot());
            }
            c.setMachineActive(false);
            try(var d=SyncDiagnostics.capture(c)){
                MixerCombustionGameTests.ticks(c,1000);c.rebuildTopology();
                h.assertTrue(d.count("MultiblockMixer.dirty")==0 && d.count("MultiblockMixer.referenceUpdate")==0,"Disabled/cache rebuild requests updates");
                com.mojang.logging.LogUtils.getLogger().info("Slice3 disabled 1000 ticks + cache rebuild counts={}",d.snapshot());
            }
            var correct=c.assembly().orElseThrow();
            try{
                var field=MultiblockMixer.class.getDeclaredField("assembly");field.setAccessible(true);
                field.set(c,new MixerAssembly(correct.impeller(),correct.shaftLength(),correct.diameter(),correct.radiusReduction(),correct.floorInsetPixels()==0?MixerVesselGeometry.SHELL_DEPTH_PIXELS:0));
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            try(var d=SyncDiagnostics.capture(c)){
                c.recheckPhysicalAssembly();
                h.assertTrue(c.assembly().orElseThrow().equals(correct) && d.count("MultiblockMixer.dirty")==0 && d.count("MultiblockMixer.referenceUpdate")==1,"Changed derived descriptor must sync without dirtying authoritative state");
                com.mojang.logging.LogUtils.getLogger().info("Slice3 changed derived Mixer descriptor counts={}",d.snapshot());
            }
            h.succeed();
        });
    }
    private static void call(Object c,String name){
        try{var method=c.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(c);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void mixerIdleAndActivationNotifications(GameTestHelper h){
        MixerGameTests.rig(h);h.runAtTickTime(35,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(CONTROLLER)).getMultiblockController().orElseThrow();
            try(var d=SyncDiagnostics.capture(c)){
                MixerCombustionGameTests.ticks(c,1000);
                h.assertTrue(d.snapshot().isEmpty(),"Idle Mixer performed notification work");
                c.setMachineActive(false);c.setMachineActive(false);
                h.assertTrue(d.count("MultiblockMixer.dirty")==1,"Activation duplicate/missing dirty mark");
                h.assertTrue(!c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.NetworkUpdate).getBoolean("active"),"Client activation missing");
                h.assertTrue(!c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync).getBoolean("active"),"Persistent activation missing");
                com.mojang.logging.LogUtils.getLogger().info("Slice3 Mixer idle 1000 ticks then disable + explicit network/full serialization counts={}",d.snapshot());
            }
            com.mojang.logging.LogUtils.getLogger().info("Slice3 Mixer controller uncompressed network/full NBT bytes={}/{}",nbtBytes(c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.NetworkUpdate)),nbtBytes(c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync)));
            h.succeed();
        });
    }
    private static final BlockPos HEAD=new BlockPos(3,3,3);
    private static net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity head(GameTestHelper h){return (net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatHeadEntity)h.getBlockEntity(HEAD);}
    private static void vatRig(GameTestHelper h){VatGameTests.build(h,5);head(h).encode(net.impactvector.mobvats.gamecontent.item.ItemEssence.COW);}
    @GameTest(template="empty",timeoutTicks=60)
    public static void specimenChangesPersistAndNoopDoesNotNotify(GameTestHelper h){
        vatRig(h);h.runAtTickTime(35,()->{
            var head=head(h);var c=head.getMultiblockController().orElseThrow();var state=new CompoundTag();state.putBoolean("Sheared",true);
            try(var d=SyncDiagnostics.capture(c,head)){
                long revision=head.getRenderRevision();head.updateSpecimenState(state);head.updateSpecimenState(state.copy());
                h.assertTrue(d.count("MultiblockVat.partDirty")==1 && d.count("MultiblockVat.partUpdate")==1 && head.getRenderRevision()==revision+1,"Head duplicate or missing persistence/presentation notification");
                h.assertTrue(d.count("MultiblockVat.dirty")==0,"Part-local specimen mutation dirtied controller");
                var saved=head.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);
                var network=head.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.NetworkUpdate);
                h.assertTrue(saved.getCompound("specimen_state").getBoolean("Sheared") && network.getCompound("specimen_state").getBoolean("Sheared"),"Specimen state missing from save/client data");
                com.mojang.logging.LogUtils.getLogger().info("Slice3 Head mutation + no-op counts={}",d.snapshot());
            }h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void vatBlockedIdleDisabledAndCacheRebuild(GameTestHelper h){
        vatRig(h);h.runAtTickTime(35,()->{
            var c=head(h).getMultiblockController().orElseThrow();
            // A deterministic blocked profile isolates cursor notifications from valid production timers.
            var registry=new net.impactvector.mobvats.gamecontent.multiblock.vat.process.MobProcessRegistry();
            registry.register(head(h).getIdentity(),new net.impactvector.mobvats.gamecontent.multiblock.vat.process.MobProcessRegistry.Profile(java.util.List.of()));
            var engine=new net.impactvector.mobvats.gamecontent.multiblock.vat.process.VatProcessEngine(c,registry.freeze());engine.assembled();
            for(int i=0;i<20;i++)engine.tick();
            try(var d=SyncDiagnostics.capture(c)){
                for(int i=0;i<1000;i++)engine.tick();
                h.assertTrue(d.count("MultiblockVat.dirty")==0,"Unchanged single-Head cursor dirtied controller");
                com.mojang.logging.LogUtils.getLogger().info("Slice3 Vat blocked 1000 ticks counts={}",d.snapshot());
            }
            c.setMachineActive(false);
            try(var d=SyncDiagnostics.capture(c)){
                for(int i=0;i<1000;i++)call(c,"updateServer");c.rebuildTopology();
                h.assertTrue(d.count("MultiblockVat.dirty")==0 && d.count("MultiblockVat.referenceUpdate")==0,"Disabled Vat/cache rebuild requested updates");
                com.mojang.logging.LogUtils.getLogger().info("Slice3 Vat disabled 1000 ticks + cache rebuild counts={}",d.snapshot());
            }h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void queuedStructureChangeCannotRunAfterPause(GameTestHelper h){
        MixerGameTests.rig(h);h.runAtTickTime(35,()->{
            var part=(MixerControllerEntity)h.getBlockEntity(CONTROLLER);var c=part.getMultiblockController().orElseThrow();
            net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitStructure.changed(h.getLevel(),h.absolutePos(CONTROLLER),true);
            part.onChunkUnloaded();h.assertTrue(c.isPaused(),"Fixture did not pause");
            try(var d=SyncDiagnostics.capture(c)){
                net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitStructure.tick(new net.neoforged.neoforge.event.tick.LevelTickEvent.Pre(()->true,h.getLevel()));
                c.conduitChanged(true);
                h.assertTrue(d.count("MultiblockMixer.structureDelivery")==0 && d.count("MultiblockMixer.referenceUpdate")==0 && d.count("MultiblockMixer.dirty")==0,"Paused notification delivered structural/network work");
            }part.clearRemoved();h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void repeatedRestoreDoesNotDuplicateNeighborDelivery(GameTestHelper h){
        MixerGameTests.rig(h);h.runAtTickTime(35,()->{
            var part=(MixerControllerEntity)h.getBlockEntity(CONTROLLER);var c=part.getMultiblockController().orElseThrow();
            for(int i=0;i<3;i++)call(c,"onMachineRestored");
            var event=new net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent(h.getLevel(),h.absolutePos(CONTROLLER),part.getBlockState(),java.util.EnumSet.allOf(net.minecraft.core.Direction.class),false);
            try(var d=SyncDiagnostics.capture(c)){
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
                h.assertTrue(d.count("MultiblockMixer.neighborDelivery")==1,"Restoration duplicated subscription delivery");
                part.onChunkUnloaded();net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
                h.assertTrue(d.count("MultiblockMixer.neighborDelivery")==1,"Paused snapshot received delivery");
            }part.clearRemoved();h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void changedVatCadenceNotifiesOnceAndSavesProgress(GameTestHelper h){
        vatRig(h);h.runAtTickTime(35,()->{
            var c=head(h).getMultiblockController().orElseThrow();
            var definition=new net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessDefinition("diagnostic", "diagnostic",net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessDefinition.Stage.TICK,id->true,java.util.List.of(),new net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessDefinition.Progress(200,true,ctx->1),net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProcessDefinition.Backpressure.NONE,null,java.util.List.of(),java.util.List.of());
            var registry=new net.impactvector.mobvats.gamecontent.multiblock.vat.process.MobProcessRegistry();registry.register(head(h).getIdentity(),new net.impactvector.mobvats.gamecontent.multiblock.vat.process.MobProcessRegistry.Profile(java.util.List.of(definition)));
            var engine=new net.impactvector.mobvats.gamecontent.multiblock.vat.process.VatProcessEngine(c,registry.freeze());engine.assembled();
            try(var d=SyncDiagnostics.capture(c)){
                for(int i=0;i<20;i++)h.assertTrue(engine.tick(),"Authoritative progress not reported");
                h.assertTrue(d.count("MultiblockVat.dirty")==20,"Cadence duplicated authoritative progress notification");
                var saved=new CompoundTag();engine.save(saved,h.getLevel().registryAccess());
                var restored=new net.impactvector.mobvats.gamecontent.multiblock.vat.process.VatProcessEngine(c,registry);restored.load(saved,h.getLevel().registryAccess());
                var roundTrip=new CompoundTag();restored.save(roundTrip,h.getLevel().registryAccess());
                h.assertTrue(saved.equals(roundTrip),"Progress/cursor persistence changed");
                com.mojang.logging.LogUtils.getLogger().info("Slice3 Vat active deterministic progress 20 ticks counts={}",d.snapshot());
            }h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void partResourceAndDemandNotifications(GameTestHelper h){
        vatRig(h);
        var itemPos=new BlockPos(3,2,1);var toolPos=new BlockPos(3,2,5);
        h.setBlock(itemPos,net.impactvector.mobvats.gamecontent.Content.state(net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType.ItemPort));
        h.setBlock(toolPos,net.impactvector.mobvats.gamecontent.Content.state(net.impactvector.mobvats.gamecontent.multiblock.vat.VatPartType.ToolPort));
        h.runAtTickTime(35,()->{
            var port=(net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatItemPortEntity)h.getBlockEntity(itemPos);
            var tool=(net.impactvector.mobvats.gamecontent.multiblock.vat.part.VatToolPortEntity)h.getBlockEntity(toolPos);
            var c=head(h).getMultiblockController().orElseThrow();
            try(var d=SyncDiagnostics.capture(c,tool)){
                tool.setHeldItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                h.assertTrue(d.count("MultiblockVat.partDirty")==1 && d.count("MultiblockVat.partUpdate")==1 && d.count("MultiblockVat.dirty")==0,"Tool inventory duplicate/missing notification");
            }
            try(var d=SyncDiagnostics.capture(c,port)){
                var beef=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEEF,3);
                port.demandOutputs(java.util.List.of(beef));port.demandOutputs(java.util.List.of(beef.copyWithCount(5)));
                h.assertTrue(d.count("MultiblockVat.partDirty")==1,"Unchanged retained demand dirtied port");
                port.staging().insertItem(0,beef,false);
                h.assertTrue(d.count("MultiblockVat.partDirty")==2 && d.count("MultiblockVat.partUpdate")==1 && d.count("MultiblockVat.dirty")==0,"Part storage transfer dirtied controller or lost update");
                var saved=port.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);
                var update=port.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.NetworkUpdate);
                h.assertTrue(saved.getCompound("staging").equals(update.getCompound("staging")),"Client resource storage differs from persistent state");
                var packet=port.getUpdatePacket();h.assertTrue(packet!=null && d.count("MultiblockVat.packetConstructed")==1,"Packet construction diagnostic missing");
                com.mojang.logging.LogUtils.getLogger().info("Slice3 Vat part transfer/demand counts={} uncompressed part NBT bytes={}",d.snapshot(),nbtBytes(update));
            }
            com.mojang.logging.LogUtils.getLogger().info("Slice3 Vat controller uncompressed network/full NBT bytes={}/{}",nbtBytes(c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.NetworkUpdate)),nbtBytes(c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync)));
            h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void startupPresentationAdvancesWithoutNotifications(GameTestHelper h){
        MixerGameTests.rig(h);h.setBlock(DRIVE,CreativeContent.block(CreativeDeviceType.DRIVE));
        h.runAtTickTime(35,()->{
            var c=((MixerControllerEntity)h.getBlockEntity(CONTROLLER)).getMultiblockController().orElseThrow();
            var saved=c.syncDataTo(new CompoundTag(),h.getLevel().registryAccess(),SyncReason.FullSync);
            c.syncDataFrom(saved,h.getLevel().registryAccess(),SyncReason.FullSync);MixerCombustionGameTests.ticks(c,1);
            c.rotorAnimation().reset();
            try(var d=SyncDiagnostics.capture(c)){
                // Invoke the presentation callback headlessly; no Minecraft client is launched.
                for(int i=0;i<19;i++)call(c,"updateClient");
                double expected=MixerRotorAnimation.OPERATING_DEGREES_PER_TICK*190/c.spinUpTicks();
                h.assertTrue(c.startupTicks()==20 && Math.abs(c.rotorAnimation().angle(1)-expected)<1e-9,"Startup presentation did not interpolate the sampled linear ramp");
                for(int i=0;i<c.spinUpTicks()+20;i++)call(c,"updateClient");
                h.assertTrue(c.startupTicks()==c.spinUpTicks() && c.operatingState()==MixerOperatingState.SPINNING_UP,"Client extrapolation changed authoritative operating state or exceeded duration");
                h.assertTrue(d.snapshot().isEmpty(),"Client presentation callback requested persistence/network work");
            }h.succeed();
        });
    }
}
