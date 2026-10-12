package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.*;
import net.impactvector.mobvats.gamecontent.multiblock.common.AttachedPartCollection;
import net.impactvector.mobvats.gamecontent.multiblock.common.ActiveBoundaryPort;
import it.zerono.mods.zerocore.lib.multiblock.*;
import it.zerono.mods.zerocore.lib.multiblock.cuboid.AbstractCuboidMultiblockController;
import it.zerono.mods.zerocore.lib.multiblock.validation.IMultiblockValidator;
import net.impactvector.mobvats.gamecontent.item.ItemEssence;
import net.impactvector.mobvats.gamecontent.fluid.ProductFluids;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.ProductBuffers;
import net.impactvector.mobvats.gamecontent.multiblock.mixer.process.SlurryBalance;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.MachineMetrics;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.KillOutputBuffer;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.CowBalance;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.CowProcesses;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.VatProcessEngine;
import it.zerono.mods.zerocore.lib.data.IoDirection;
import it.zerono.mods.zerocore.lib.fluid.handler.FluidHandlerPolicyWrapper;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class MultiblockVat extends net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitCuboidController<MultiblockVat> implements it.zerono.mods.zerocore.lib.IActivableMachine {
    private boolean active = true;
    @Override public boolean isMachineActive() { return active; }
    @Override public void setMachineActive(boolean value) {
        if (active == value) return;
        active = value;
        statusTick = Long.MIN_VALUE;
        // Operational callbacks belong to a live machine, never an unloading structure.
        if (isPaused()) return;
        getConnectedParts().forEach(value ? IMultiblockPart::onMachineActivated : IMultiblockPart::onMachineDeactivated);
        callOnLogicalServer(() -> processStateChanged(true));
    }
    private List<MobAssembly> assemblies = List.of();
    private final Set<AbstractVatEntity> attachedPorts = new HashSet<>();
    private final AttachedPartCollection<VatBoundaryEntity> boundaryDevices = new AttachedPartCollection<>(VatBoundaryEntity.class,Comparator.comparing(VatBoundaryEntity::getBlockPos,VatTopologyCompiler.POSITION));
    private final AttachedPartCollection<ActiveBoundaryPort> activePorts = new AttachedPartCollection<>(ActiveBoundaryPort.class,Comparator.comparing(ActiveBoundaryPort::getBlockPos,VatTopologyCompiler.POSITION));
    private void retireAttachedParts(){attachedPorts.clear();boundaryDevices.clear();activePorts.clear();}
    private void rebuildAttachedParts(){retireAttachedParts();getConnectedParts().forEach(this::registerAttachedPart);}
    private void registerAttachedPart(Object part){
        if(part instanceof AbstractVatPortEntity || part instanceof VatActor || part instanceof VatBoundaryEntity)attachedPorts.add((AbstractVatEntity)part);
        boundaryDevices.add(part);activePorts.add(part);
    }
    private VatTopology topology = VatTopology.EMPTY;
    private final Map<BlockPos,SpecimenClearance> clearances=new HashMap<>();
    public SpecimenClearance specimenClearance(BlockPos head){return clearances.get(head);}
    private long topologyRevision;
    private final VatProcessEngine processEngine =
        new VatProcessEngine(this);
    private final MachineMetrics metrics = new MachineMetrics();
    private final KillOutputBuffer killOutputs = new KillOutputBuffer();
    private int clientKillStacks;
    private long clientKillItems;
    public KillOutputBuffer killOutputs() { return killOutputs; }
    public int killOutputStackCount() { return getWorld().isClientSide ? clientKillStacks : killOutputs.stackCount()+toolPorts().stream().mapToInt(t->t.results().stackCount()).sum(); }
    public long killOutputItemCount() { return getWorld().isClientSide ? clientKillItems : killOutputs.itemCount()+toolPorts().stream().mapToLong(t->t.results().itemCount()).sum(); }
    private long clientExperienceMb, statusTick = Long.MIN_VALUE;
    private final Map<BlockPos, CowProcesses.Snapshot> clientStates = new HashMap<>();
    private VatStatusSnapshot menuStatus = VatStatusSnapshot.DISCONNECTED;
    public MachineMetrics metrics() { return metrics; }
    public VatStatusSnapshot statusSnapshot() {
        long tick = getWorld().getGameTime() / CowBalance.PROCESS_CADENCE;
        if (statusTick != tick) { statusTick = tick; menuStatus = VatStatusSnapshot.capture(this, attachedPorts); }
        return menuStatus;
    }
    public VatProcessEngine processEngine(){return processEngine;}
    public List<VatItemPortEntity> itemPorts(){return attachedPorts.stream().filter(VatItemPortEntity.class::isInstance).map(VatItemPortEntity.class::cast).sorted(Comparator.comparing(VatItemPortEntity::getBlockPos,VatTopologyCompiler.POSITION)).toList();}
    public List<VatToolPortEntity> toolPorts(){return attachedPorts.stream().filter(VatToolPortEntity.class::isInstance).map(VatToolPortEntity.class::cast).toList();}
    public long getExperienceMb() { return getWorld().isClientSide ? clientExperienceMb : processEngine.experienceMb(); }
    public CowProcesses.Snapshot cowState(BlockPos head) { return getWorld().isClientSide
        ? clientStates.getOrDefault(head, new CowProcesses.Snapshot("IDLE",0,0,0)) : processEngine.snapshot(head); }
    /** Cosmetic post-commit pulse; never delays or retries a production transaction. */
    public void toolInteracted(VatToolPortEntity tool) { tool.setInteractionStart(getWorld().getGameTime()); }
    public void toolInteracted(VatToolPortEntity tool,BlockPos head){tool.selectTarget(head);toolInteracted(tool);for(var assembly:assemblies)assembly.head().setPresentation(VatPresentation.compile(this,assembly,attachedPorts));}

    public String productionStatus() {
        String specimens=assemblies.stream().map(a->a.head().getIdentity()+" @ "+a.head().getBlockPos().toShortString()+": "+
            (topology.operational(a.head().getBlockPos())?a.head().lifeStage().name():"inactive: harness obstruction"))
            .collect(java.util.stream.Collectors.joining("; "));
        return products.status()+" | Specimens "+assemblies.size()+" ["+specimens+"] | XP "+getExperienceMb()+" mB | Items buffered "+killOutputStackCount()+" stacks";
    }

    // ZeroCore's dirty helper already notifies the reference block, even for sync=false.
    public void processStateChanged(boolean sync) { statusTick = Long.MIN_VALUE; markReferenceCoordDirty(); }
    public int milkAmount() {
        var tank=milkBuffer();return tank!=null && tank.getFluid().is(NeoForgeMod.MILK.get()) ? tank.getFluidAmount() : 0;
    }
    public FluidStack fluidContents() { var tank=milkBuffer();return tank==null?FluidStack.EMPTY:tank.getFluid().copy(); }
    public boolean produceMilk() {
        if(milkBuffer()==null)return false;
        var milk = new FluidStack(NeoForgeMod.MILK.get(),CowBalance.MILK_PER_INTERVAL);
        if (milkBuffer().fill(milk,FluidAction.SIMULATE) != CowBalance.MILK_PER_INTERVAL) return false;
        boolean produced = milkBuffer().fill(milk,FluidAction.EXECUTE) == CowBalance.MILK_PER_INTERVAL;
        if (produced) metrics.record(MachineMetrics.MILK, CowBalance.MILK_PER_INTERVAL);
        return produced;
    }
    public boolean consumeMilk() {
        if (milkAmount() < CowBalance.MILK_PER_INTERVAL) return false;
        return milkBuffer().drain(new FluidStack(NeoForgeMod.MILK.get(),CowBalance.MILK_PER_INTERVAL),
            FluidAction.EXECUTE).getAmount() == CowBalance.MILK_PER_INTERVAL;
    }
    private final ProductBuffers products = new ProductBuffers(() -> processStateChanged(true));
    private final net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage storage=new net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage();
    private net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceRouting routing;
    public net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceRouting resourceRouting(){
        if(routing!=null)return routing;
        var endpoints=new ArrayList<net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceEndpoint>();
        for(var part:attachedPorts)if(part instanceof net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceEndpoint endpoint)endpoints.add(endpoint);
        storage.clusters().forEach(c->c.members().forEach(member->endpoints.add(new net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceEndpoint(){
            public BlockPos getBlockPos(){return member.getBlockPos();}
            public boolean routingAvailable(){return c.members().stream().noneMatch(net.impactvector.mobvats.gamecontent.multiblock.conduit.FluidBufferEntity::isRemoved);}
            public java.util.Set<net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role> roles(net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource r){return r==net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource.FLUID?java.util.Set.of(net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role.STORE):java.util.Set.of();}
            public java.util.List<net.neoforged.neoforge.fluids.capability.templates.FluidTank> fluids(net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitTopology.Role role){return java.util.List.of(c.tank());}
        })));
        return routing=new net.impactvector.mobvats.gamecontent.multiblock.conduit.ResourceRouting(topology.conduits(),endpoints);
    }
    public net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage storage(){return storage;}
    private FluidTank milkBuffer(){return products.find(ProductBuffers.MILK);}
    private final VatMediumStorage medium=new VatMediumStorage(() -> processStateChanged(true));
    public VatMediumStorage medium(){return medium;}
    private String feedSource="None";
    public ProductBuffers processFluids(){return products;}
    public int cultureAmount(){return products.cultureAmount();}
    public int feedingAmount(){return products.feedingAmount();}
    public String feedSource(){return feedSource;}
    public double frequencyMultiplier(BlockPos head){
        var clearance=clearances.get(head);if(clearance==null || clearance.shell().isEmpty())return 0.5;
        var assembly=assemblies.stream().filter(a->a.head().getBlockPos().equals(head)).findFirst().orElse(null);
        if(assembly==null)return 0.5;
        double matched=medium.frequency(assembly.head().getIdentity());
        return clearance.shell().stream().mapToDouble(p->0.5+medium.exposure(p)*(matched-0.5)).average().orElse(0.5);
    }
    public double yieldMultiplier(BlockPos head){
        var clearance=clearances.get(head);if(clearance==null || clearance.shell().isEmpty())return 0.5;
        return 0.5+1.5*clearance.creditedFraction(clearances.values());
    }
    public boolean consumeFeedingSlurry(){
        int cost=SlurryBalance.FEEDING_COST;
        if(!products.consumeFeeding(cost))return false;metrics.record("feedingConsumed",cost);feedSource="feeding slurry";return true;
    }
    public void usedSlurryFeed(){feedSource="feeding slurry";}
    public void usedItemFeed(){feedSource="item feed";}
    public int meatAmount() { return products.meatAmount(); }
    public int wasteAmount() { return products.wasteAmount(); }
    public boolean produceMeat() {
        if (!products.produceMeat()) return false;
        metrics.record(MachineMetrics.MEAT,CowBalance.MEAT_PER_HARVEST); return true;
    }
    public boolean produceWaste() {
        if (!products.produceWaste()) return false;
        metrics.record(MachineMetrics.WASTE,CowBalance.WASTE_PER_INTERVAL); return true;
    }
    public VatTopology getTopology() { return topology; }
    public long getTopologyRevision() { return topologyRevision; }
    @Override protected void rebuildConduitTopology(){rebuildTopology();}
    @Override protected void invalidateConduitTopology() { topology = VatTopology.EMPTY; routing=null;storage.clear();clearances.clear();compiledFacings.clear();processEngine.retireContexts();statusTick = Long.MIN_VALUE; }
    private final java.util.Map<BlockPos,net.minecraft.core.Direction> compiledFacings=new java.util.HashMap<>();
    public void specimenStateChanged(VatHeadEntity head){
        if(!getWorld().isClientSide && isAssembled() && compiledFacings.get(head.getBlockPos())!=head.getSpecimenFacing())rebuildTopology();
    }
    public void rebuildTopology() {
        // ZeroCore also assembles client controllers for bounds/model data. Routes are server-only.
        if (getWorld().isClientSide) { topology = VatTopology.EMPTY; return; }
        topology = isAssembled() ? VatTopologyCompiler.compile(getWorld(), getBoundingBox().getMin(), getBoundingBox().getMax(), assemblies, attachedPorts) : VatTopology.EMPTY;
        routing=null;
        storage.compile(net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.members(getWorld(),getBoundingBox().getMin(),getBoundingBox().getMax()),topology.conduits());
        storage.sinks(attachedPorts.stream().filter(VatBoundaryEntity.class::isInstance).map(VatBoundaryEntity.class::cast).filter(e->e.getPartType()==VatPartType.FluidVoid).sorted(Comparator.comparing(VatBoundaryEntity::getBlockPos,VatTopologyCompiler.POSITION)).toList());
        var pumpFaces=new HashMap<BlockPos,Direction>();
        if(isAssembled())for(var part:attachedPorts)if(part.getPartType()==VatPartType.PullPump || part.getPartType()==VatPartType.PushPump){var face=BoundaryDevices.outward(part.getBlockPos(),getBoundingBox().getMin(),getBoundingBox().getMax());if(face!=null)pumpFaces.put(part.getBlockPos(),face.getOpposite());}
        storage.pumpInward(pumpFaces);
        var services=new java.util.HashMap<BlockPos,net.impactvector.mobvats.gamecontent.multiblock.conduit.ServiceStaging>();
        for(var assembly:assemblies){
            var drain=assembly.drain();services.put(drain.getBlockPos(),drain.service());
            drain.service().tank(new FluidStack(net.impactvector.mobvats.gamecontent.fluid.ProductFluids.EXPERIENCE.get(),1));
            var definition=net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveMobDefinitionRegistry.resolve(assembly.head().getIdentity());
            if(definition!=null){
                for(var output:definition.secondary_outputs())drain.service().tank(new FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(output.fluid())),1)).setCapacity(Math.max(1000,Math.multiplyExact(output.amount(),2)));
                for(var process:definition.processes())for(var output:process.outputs())if(output.type().equals("fluid"))drain.service().tank(new FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(output.fluid())),1)).setCapacity(Math.max(1000,Math.multiplyExact(output.amount(),output.yield_scaled()?2:1)));
            }
        }
        for(var part:attachedPorts)if(part instanceof VatBlenderEntity blender){blender.service().tank(new FluidStack(net.impactvector.mobvats.gamecontent.fluid.ProductFluids.MEAT.get(),1)).setCapacity(Math.max(2000,assemblies.stream().map(a->net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveMobDefinitionRegistry.resolve(a.head().getIdentity())).filter(d->d!=null && d.lifecycle()).mapToInt(d->Math.multiplyExact(d.harvest().blender_amount(),2)).max().orElse(2000)));services.put(blender.getBlockPos(),blender.service());}
        for(var tool:toolPorts()){
            services.put(tool.getBlockPos(),tool.service());
            for(var assembly:assemblies){var definition=net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveMobDefinitionRegistry.resolve(assembly.head().getIdentity());
                if(definition!=null)for(var output:definition.secondary_outputs())if(output.container()!=null && topology.actors().stream().anyMatch(r->r.actor().equals(tool.getBlockPos()) && r.head().equals(assembly.head().getBlockPos()) && r.use().equals(output.container().actor_use())))
                    tool.service().tank(new FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(output.fluid())),1)).setCapacity(Math.max(1000,Math.multiplyExact(output.amount(),2)));
            }
        }
        storage.services(services);
        products.bind(storage);
        medium.compile(getWorld(),getBoundingBox().getMin(),getBoundingBox().getMax());medium.migrate(getWorld());
        attachedPorts.stream().filter(VatFluidPortEntity.class::isInstance).map(VatFluidPortEntity.class::cast).forEach(VatFluidPortEntity::refreshRouting);
        clearances.clear();for(var assembly:assemblies)clearances.put(assembly.head().getBlockPos().immutable(),
            SpecimenClearance.inspect(getWorld(),assembly.head().geometry(),assembly.head().getBlockPos(),assemblies));
        adoptConduitGeometry(topology.physical().geometry());
        compiledFacings.clear();assemblies.forEach(a->compiledFacings.put(a.head().getBlockPos().immutable(),a.head().getSpecimenFacing()));
        ++topologyRevision;
        statusTick = Long.MIN_VALUE;
        for (var assembly : assemblies)
            assembly.head().setPresentation(VatPresentation.compile(this, assembly, attachedPorts));
    }
    public IFluidHandler fluidHandler(IoDirection direction) {
        if (!isAssembled()) return EmptyFluidHandler.INSTANCE;
        return direction.isInput() ? products.inputHandler() : FluidHandlerPolicyWrapper.outputOnly(products);
    }
    public MultiblockVat(Level level) { super(level); products.bind(storage); }
    public List<VatBoundaryEntity> voids(BlockPos endpoint,net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitResource resource){
        return attachedPorts.stream().filter(VatBoundaryEntity.class::isInstance).map(VatBoundaryEntity.class::cast).filter(e->!e.isRemoved() && e.isVoid() && e.resource()==resource && storage.connectedEndpoints(endpoint,e.getBlockPos(),resource)).sorted(Comparator.comparing(VatBoundaryEntity::getBlockPos,VatTopologyCompiler.POSITION)).toList();
    }
    public List<MobAssembly> getAssemblies() { return assemblies; }
    public long specimenHeadCount(){return getConnectedParts().stream().filter(VatHeadEntity.class::isInstance).count();}
    @Override protected boolean isMachineWhole(IMultiblockValidator validator) {
        assemblies = List.of();
        if (!super.isMachineWhole(validator)) return false;
        var parts = getConnectedParts().stream().map(p -> (AbstractVatEntity)p).toList();
        if (count(parts,VatPartType.Controller)!=1 || count(parts,VatPartType.Head)<1) {
            validator.setLastError("multiblock.mobvats.one_specimen",count(parts,VatPartType.Controller),count(parts,VatPartType.Harness),count(parts,VatPartType.Sump),count(parts,VatPartType.Head));return false;
        }
        var heads=parts.stream().filter(VatHeadEntity.class::isInstance).map(VatHeadEntity.class::cast)
            .sorted(Comparator.comparing(VatHeadEntity::getBlockPos,VatTopologyCompiler.POSITION)).toList();
        for(var head:heads)
        if(head.getIdentity()==null || head.geometry()==null || (!getWorld().isClientSide && net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveMobDefinitionRegistry.resolve(head.getIdentity())==null)) {
            validator.setLastError(head.getBlockPos(),"multiblock.mobvats.geometry",head.geometryError()==null?"Encoded Head with a valid definition required":head.geometryError());return false;
        }
        try {
            var claims=heads.stream().map(head->new SpecimenGeometry.Claim(head.getBlockPos(),head.geometry())).toList();
            var owners=SpecimenGeometry.ownership(claims);
            var min=getBoundingBox().getMin();var max=getBoundingBox().getMax();
            for(var cell:owners.keySet())if(cell.getX()<=min.getX() || cell.getX()>=max.getX() || cell.getY()<=min.getY() || cell.getY()>=max.getY() || cell.getZ()<=min.getZ() || cell.getZ()>=max.getZ())
                throw new IllegalArgumentException("Specimen occupied cell outside vat interior @ "+cell.toShortString());
            claims.forEach(claim->SpecimenGeometry.substrate(getWorld(),claim));
            for(var part:parts)if(part instanceof VatFleshEntity && !owners.containsKey(part.getBlockPos())) {
                validator.setLastError(part.getBlockPos(),"multiblock.mobvats.extra_flesh",count(parts,VatPartType.Flesh),owners.size()-heads.size());return false;
            }
            var harnesses=parts.stream().filter(p->p instanceof VatHarnessEntity).map(p->(VatHarnessEntity)p).toList();
            var drains=parts.stream().filter(p->p instanceof VatSumpEntity).map(p->(VatSumpEntity)p).toList();
            assemblies=SpecimenGeometry.associateAll(getWorld(),heads,harnesses,drains);
        } catch(IllegalArgumentException error) {
            validator.setLastError("multiblock.mobvats.geometry",error.getMessage());return false;
        }
        return true;
    }
    private static long count(List<AbstractVatEntity> parts, VatPartType type) {
        return parts.stream().filter(p -> p.getPartType() == type).count();
    }
    @Override protected void onMachineAssembled() {
        super.onMachineAssembled();
        rebuildAttachedParts();
        rebuildTopology();
        if (!getWorld().isClientSide) processEngine.assembled();
        markMultiblockForRenderUpdate();
    }
    @Override protected void onMachineRestored() { onMachineAssembled(); }
    private void clearPresentation() {
        clearConduitGeometry();
        clearances.clear();
        // ZeroCore's Head onPostMachineBroken callback clears presentation for loaded parts.
        // Controller teardown must not traverse parts and notify unloading chunks.
        statusTick = Long.MIN_VALUE;
    }
    @Override protected void onMachinePaused() {
        // Presentation is rebuilt on restoration. Never call Head setters during unload.
        clearConduitGeometry(); assemblies = List.of(); invalidateConduitTopology();retireAttachedParts();
    }
    @Override protected void onMachineDisassembled() { clearPresentation(); assemblies = List.of(); invalidateConduitTopology();retireAttachedParts(); if(getWorld().isClientSide)markMultiblockForRenderUpdate(); }
    @Override protected void onPartAdded(IMultiblockPart<MultiblockVat> part) {
        invalidateConduitBounds();routing=null;
        if(!isPaused())registerAttachedPart(part);
    }
    @Override protected void onPartRemoved(IMultiblockPart<MultiblockVat> part) { invalidateConduitBounds(); clearConduitGeometry(); attachedPorts.remove(part);boundaryDevices.remove(part);activePorts.remove(part); invalidateConduitTopology(); }
    @Override protected void onAssimilate(IMultiblockController<MultiblockVat> other) {
        // Normal reconnection joins an empty fragment to the persisted controller.
        // New MEAT/WASTE stores preserve committed contents, including reconnection overflow.
        if (other instanceof MultiblockVat vat) {
            active &= vat.active;
            processEngine.assimilate(vat.processEngine);
            killOutputs.assimilate(vat.killOutputs);
            products.assimilate(vat.products);medium.assimilate(vat.medium);
            metrics.assimilate(vat.metrics);

        }
    }
    @Override protected void onAssimilated(IMultiblockController<MultiblockVat> other) { assemblies = List.of(); invalidateConduitTopology(); retireAttachedParts(); processEngine.clear(); killOutputs.clear(); products.clear();medium.clear(); metrics.load(new CompoundTag()); }
    @Override protected boolean updateServer() {
        if(isAssembled())storage.evacuate();
        if (!isAssembled()) return false;
        BoundaryDevices.tickOrdered(this,boundaryDevices.parts());
        metrics.tick();
        boolean changed=active ? processEngine.tick() : getWorld().getGameTime()%VatProcessEngine.CADENCE==0 && processEngine.transferCommittedOutputs();
        if(!active && changed)processStateChanged(true);
        return ActiveBoundaryPort.tickOrdered(activePorts.parts(),getWorld().getGameTime()) || changed;
    }
    @Override protected void updateClient() {}
    @Override protected int getMinimumXSize() { return 3; }
    @Override protected int getMinimumYSize() { return 4; }
    @Override protected int getMinimumZSize() { return 3; }
    @Override protected int getMaximumXSize() { return 16; }
    @Override protected int getMaximumYSize() { return 16; }
    @Override protected int getMaximumZSize() { return 16; }
    private boolean conduitAllowed(Level level,int x,int y,int z){
        return net.impactvector.mobvats.gamecontent.multiblock.conduit.ConduitStructure.allows(level,new BlockPos(x,y,z),getBoundingBox().getMin(),getBoundingBox().getMax(),false);
    }
    // All shell positions must be connected vat parts; ZeroCore2 routes those to part validation.
    @Override protected boolean isBlockGoodForFrame(Level w, int x, int y, int z, IMultiblockValidator v) { return conduitAllowed(w,x,y,z); }
    @Override protected boolean isBlockGoodForTop(Level w, int x, int y, int z, IMultiblockValidator v) { return conduitAllowed(w,x,y,z); }
    @Override protected boolean isBlockGoodForBottom(Level w, int x, int y, int z, IMultiblockValidator v) { return conduitAllowed(w,x,y,z); }
    @Override protected boolean isBlockGoodForSides(Level w, int x, int y, int z, IMultiblockValidator v) { return conduitAllowed(w,x,y,z); }
    @Override protected boolean isBlockGoodForInterior(Level w, int x, int y, int z, IMultiblockValidator v) {
        return SpecimenGeometry.medium(w,new BlockPos(x,y,z)) || conduitAllowed(w,x,y,z);
    }
    @Override public CompoundTag syncDataTo(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataTo(data,registries,reason);
        data.putBoolean("active",active);

        data.put("products",products.save(registries));data.put("vatMedium",medium.save(registries));data.putString("feedSource",feedSource);
        data.put("mediumPresentationLevels",medium.presentationLevels());

        if (reason == SyncReason.FullSync) {
            processEngine.save(data,registries); data.put("metrics", metrics.save());
            data.put("killOutputs",killOutputs.save(registries));
        } else {
            data.putLong("experienceMb", getExperienceMb());
            data.putInt("killOutputStacks",killOutputStackCount()); data.putLong("killOutputItems",killOutputItemCount());
            var states = new net.minecraft.nbt.ListTag();
            for (var head : topology.assemblies()) {
                var state = processEngine.snapshot(head); var tag = new CompoundTag();
                tag.putLong("head", head.asLong()); tag.putString("cycle", state.replacement());
                tag.putInt("replacementTicks", state.replacementTicks()); tag.putInt("milkTicks", state.milkTicks());
                tag.putInt("pendingCount", state.pendingItemCount()); states.add(tag);
            }
            data.put("cowStatus", states);
        }
        return data;
    }
    @Override public void syncDataFrom(CompoundTag data, HolderLookup.Provider registries, SyncReason reason) {
        super.syncDataFrom(data,registries,reason);
        active = !data.contains("active") || data.getBoolean("active");

        products.load(data.getCompound("products"),registries);
        medium.load(data.getCompound("vatMedium"),registries);
        if(getWorld().isClientSide)medium.loadPresentationLevels(data.getCompound("mediumPresentationLevels"));
        if(!data.getCompound("products").contains("buffers") && data.contains("fluidBuffer"))products.loadLegacyMilk(data.getCompound("fluidBuffer"),registries);
        feedSource=data.contains("feedSource")?data.getString("feedSource"):"item feed";
        if (reason == SyncReason.FullSync) {
            if (data.contains("cowProcesses") || data.contains("specimenProcesses")) processEngine.load(data,registries);
            metrics.load(data.getCompound("metrics"));
            killOutputs.load(data.getList("killOutputs",net.minecraft.nbt.Tag.TAG_COMPOUND),registries);
        } else if (getWorld().isClientSide) {
            clientExperienceMb = Math.max(0,data.getLong("experienceMb")); clientStates.clear();
            clientKillStacks = Math.max(0,data.getInt("killOutputStacks")); clientKillItems = Math.max(0,data.getLong("killOutputItems"));
            var states = data.getList("cowStatus", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < states.size(); ++i) {
                var state = states.getCompound(i);
                clientStates.put(BlockPos.of(state.getLong("head")), new CowProcesses.Snapshot(state.getString("cycle"),
                    state.getInt("replacementTicks"),state.getInt("milkTicks"),state.getInt("pendingCount")));
            }
        }
        statusTick = Long.MIN_VALUE;
    }
}
