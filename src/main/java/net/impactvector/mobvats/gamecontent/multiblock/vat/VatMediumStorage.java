package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.impactvector.mobvats.gamecontent.fluid.compound.CultureFouling;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.CultureProfiles;

/** Two fractions of one homogeneous environment. Capacity changes never discard either fraction. */
public final class VatMediumStorage extends FluidTank {
    private final NavigableMap<Integer,Integer> levels=new TreeMap<>();
    private final Map<BlockPos,Integer> cells=new HashMap<>();
    private final List<State> reconnectionConflicts=new ArrayList<>();
    private FluidStack cleanPrototype=FluidStack.EMPTY,dirtyPrototype=FluidStack.EMPTY;
    private int cleanAmount,dirtyAmount;
    private long presentationRevision;
    public long presentationRevision(){return presentationRevision;}
    public record State(FluidStack clean,FluidStack dirty,int cleanAmount,int dirtyAmount){
        public State { clean=clean.copyWithAmount(1);dirty=dirty.copyWithAmount(1);if(cleanAmount<0 || dirtyAmount<0)throw new IllegalArgumentException("Negative medium");Math.addExact(cleanAmount,dirtyAmount); }
        public int total(){return Math.addExact(cleanAmount,dirtyAmount);}
    }
    private boolean migrated;
    private String migrationDiagnostic="";
    private final Runnable changed;
    public VatMediumStorage(Runnable changed){super(0);this.changed=changed;}
    @Override protected void onContentsChanged(){changed.run();}
    public FluidStack cleanPrototype(){return cleanPrototype.copy();}
    public FluidStack dirtyPrototype(){return dirtyPrototype.copy();}
    public int cleanAmount(){return cleanAmount;}
    public int dirtyAmount(){return dirtyAmount;}
    public State snapshot(){return new State(cleanPrototype,dirtyPrototype,cleanAmount,dirtyAmount);}
    public void restore(State state){cleanPrototype=state.clean().copy();dirtyPrototype=state.dirty().copy();cleanAmount=state.cleanAmount();dirtyAmount=state.dirtyAmount();if(isEmpty()){cleanPrototype=FluidStack.EMPTY;dirtyPrototype=FluidStack.EMPTY;}changed.run();}
    public boolean matches(State state){return cleanAmount==state.cleanAmount() && dirtyAmount==state.dirtyAmount() && FluidStack.matches(cleanPrototype,state.clean()) && FluidStack.matches(dirtyPrototype,state.dirty());}
    public double frequency(net.minecraft.resources.ResourceLocation mob){var rule=CultureProfiles.resolve(mob,cleanPrototype);return rule==null || isEmpty()?0.5:(cleanAmount*rule.frequency()+dirtyAmount*rule.dirtyFrequency())/getFluidAmount();}
    public int foul(int amount){int converted=Math.min(cleanAmount,Math.max(0,amount));if(converted==0 || dirtyPrototype.isEmpty())return 0;cleanAmount-=converted;dirtyAmount+=converted;changed.run();return converted;}
    @Override public FluidStack getFluid(){return dirtyAmount>0?dirtyPrototype.copyWithAmount(dirtyAmount):cleanPrototype.copyWithAmount(cleanAmount);}
    @Override public int getFluidAmount(){return Math.addExact(cleanAmount,dirtyAmount);}
    @Override public boolean isEmpty(){return getFluidAmount()==0;}
    @Override public int getSpace(){return Math.max(0,getCapacity()-getFluidAmount());}
    @Override public int getTanks(){return 2;}
    @Override public int getTankCapacity(int tank){return Math.max(0,getCapacity()-(tank==0?cleanAmount:dirtyAmount));}
    @Override public FluidStack getFluidInTank(int tank){return tank==0?dirtyPrototype.copyWithAmount(dirtyAmount):cleanPrototype.copyWithAmount(cleanAmount);}
    @Override public boolean isFluidValid(FluidStack stack){return !stack.isEmpty() && (isEmpty() || FluidStack.isSameFluidSameComponents(cleanPrototype,stack) || FluidStack.isSameFluidSameComponents(dirtyPrototype,stack));}
    /** Compatibility setters/12.1 NBT always establish the entire old stack as clean. */
    @Override public void setFluid(FluidStack stack){cleanAmount=stack.getAmount();dirtyAmount=0;cleanPrototype=stack.copyWithAmount(1);dirtyPrototype=CultureFouling.dirty(cleanPrototype,CultureProfiles.wasteFormulaMb());if(stack.isEmpty()){cleanPrototype=FluidStack.EMPTY;dirtyPrototype=FluidStack.EMPTY;}}
    @Override public int fill(FluidStack stack,FluidAction action){
        if(fillRejection(stack)!=FillRejection.NONE)return 0;
        var clean=cleanPrototype;var dirty=dirtyPrototype;boolean fillingDirty;
        if(isEmpty()){
            fillingDirty=CultureFouling.hasWaste(stack);
            clean=fillingDirty?CultureFouling.clean(stack,CultureProfiles.wasteFormulaMb()):stack.copyWithAmount(1);
            dirty=CultureFouling.dirty(clean,CultureProfiles.wasteFormulaMb());
        }else fillingDirty=FluidStack.isSameFluidSameComponents(dirty,stack);
        int accepted=Math.min(stack.getAmount(),getSpace());
        if(action.execute()){cleanPrototype=clean.copy();dirtyPrototype=dirty.copy();if(fillingDirty)dirtyAmount+=accepted;else cleanAmount+=accepted;changed.run();}
        return accepted;
    }
    public enum FillRejection { NONE, EMPTY, CONFLICT, CAPACITY, INCOMPATIBLE }
    /** Shared acceptance decision for transfer and diagnostics; no state changes. */
    public FillRejection fillRejection(FluidStack stack){
        if(stack.isEmpty())return FillRejection.EMPTY;
        if(!reconnectionConflicts.isEmpty() || !migrationDiagnostic.isEmpty())return FillRejection.CONFLICT;
        if(getSpace()==0)return FillRejection.CAPACITY;
        if(isEmpty())return CultureFouling.hasWaste(stack) && CultureFouling.clean(stack,CultureProfiles.wasteFormulaMb()).isEmpty()?FillRejection.INCOMPATIBLE:FillRejection.NONE;
        return FluidStack.isSameFluidSameComponents(cleanPrototype,stack) || FluidStack.isSameFluidSameComponents(dirtyPrototype,stack)?FillRejection.NONE:FillRejection.INCOMPATIBLE;
    }
    @Override public FluidStack drain(int amount,FluidAction action){
        if(amount<=0 || isEmpty())return FluidStack.EMPTY;
        boolean dirty=dirtyAmount>0;int drained=Math.min(amount,dirty?dirtyAmount:cleanAmount);
        var result=(dirty?dirtyPrototype:cleanPrototype).copyWithAmount(drained);
        if(action.execute()){
            if(dirty)dirtyAmount-=drained;else cleanAmount-=drained;
            if(isEmpty()){cleanPrototype=FluidStack.EMPTY;dirtyPrototype=FluidStack.EMPTY;if(!reconnectionConflicts.isEmpty())restore(reconnectionConflicts.removeFirst());}
            changed.run();
        }
        return result;
    }
    @Override public FluidStack drain(FluidStack request,FluidAction action){
        if(request.isEmpty())return FluidStack.EMPTY;
        if(FluidStack.isSameFluidSameComponents(request,dirtyPrototype))return drain(Math.min(request.getAmount(),dirtyAmount),action);
        if(!FluidStack.isSameFluidSameComponents(request,cleanPrototype) || dirtyAmount>0)return FluidStack.EMPTY;
        return drain(request.getAmount(),action);
    }
    public void compile(Level level,BlockPos min,BlockPos max){
        levels.clear();cells.clear();int total=0;
        for(var p:BlockPos.betweenClosed(min.offset(1,1,1),max.offset(-1,-1,-1))){
            int available=1000-VatDisplacement.displaced(level,p);cells.put(p.immutable(),available);
            levels.merge(p.getY(),available,Integer::sum);total=Math.addExact(total,available);
        }
        setCapacity(total);
    }
    public int levelCapacity(int y){return levels.getOrDefault(y,0);}
    /** Client presentation receives server-compiled displacement; it never recomputes gameplay capacity. */
    public CompoundTag presentationLevels(){
        var tag=new CompoundTag();tag.putInt("capacity",getCapacity());var entries=new net.minecraft.nbt.ListTag();
        levels.forEach((y,amount)->{var entry=new CompoundTag();entry.putInt("y",y);entry.putInt("amount",amount);entries.add(entry);});tag.put("levels",entries);return tag;
    }
    public void loadPresentationLevels(CompoundTag tag){
        ++presentationRevision;
        levels.clear();var entries=tag.getList("levels",net.minecraft.nbt.Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++){var entry=entries.getCompound(i);levels.put(entry.getInt("y"),entry.getInt("amount"));}
        setCapacity(tag.getInt("capacity"));
    }
    public double levelFill(int y){
        int remaining=Math.min(getFluidAmount(),getCapacity());
        for(var entry:levels.entrySet()){
            if(entry.getKey()==y)return entry.getValue()==0?0:Math.min(1,Math.max(0,(double)remaining/entry.getValue()));
            remaining=Math.max(0,remaining-entry.getValue());
        }return 0;
    }
    public double exposure(BlockPos p){return cells.getOrDefault(p,0)/1000.0*levelFill(p.getY());}
    public boolean overCapacity(){return getFluidAmount()>getCapacity();}
    public void clear(){setFluid(FluidStack.EMPTY);reconnectionConflicts.clear();levels.clear();cells.clear();setCapacity(0);migrationDiagnostic="";migrated=false;changed.run();}
    public List<String> diagnostics(){var result=new ArrayList<String>();if(overCapacity())result.add("Vat medium over capacity: "+getFluidAmount()+" / "+getCapacity()+" mB; drain to recover");if(!migrationDiagnostic.isEmpty())result.add(migrationDiagnostic);if(!reconnectionConflicts.isEmpty())result.add("Conflicting reconnection media retained: "+reconnectionConflicts.stream().mapToLong(State::total).sum()+" mB; drain sequentially");return List.copyOf(result);}
    private static CompoundTag saveState(State state,HolderLookup.Provider registries){var tag=new CompoundTag();if(!state.clean().isEmpty())tag.put("cleanPrototype",state.clean().save(registries));if(!state.dirty().isEmpty())tag.put("dirtyPrototype",state.dirty().save(registries));tag.putInt("cleanAmount",state.cleanAmount());tag.putInt("dirtyAmount",state.dirtyAmount());return tag;}
    private static State readState(CompoundTag tag,HolderLookup.Provider registries){return new State(FluidStack.parseOptional(registries,tag.getCompound("cleanPrototype")),FluidStack.parseOptional(registries,tag.getCompound("dirtyPrototype")),tag.getInt("cleanAmount"),tag.getInt("dirtyAmount"));}
    public CompoundTag save(HolderLookup.Provider registries){var tag=saveState(snapshot(),registries);tag.putInt("mediumVersion",2);tag.putBoolean("legacyMigrated",migrated);tag.putString("migrationDiagnostic",migrationDiagnostic);var conflicts=new net.minecraft.nbt.ListTag();for(var state:reconnectionConflicts)conflicts.add(saveState(state,registries));tag.put("reconnectionConflicts",conflicts);return tag;}
    public void load(CompoundTag tag,HolderLookup.Provider registries){
        boolean modern=tag.getInt("mediumVersion")>=2;
        if(modern)restore(readState(tag,registries));else setFluid(FluidStack.parseOptional(registries,tag.getCompound("Fluid")));
        migrated=tag.getBoolean("legacyMigrated");migrationDiagnostic=tag.getString("migrationDiagnostic");reconnectionConflicts.clear();
        var conflicts=tag.getList("reconnectionConflicts",net.minecraft.nbt.Tag.TAG_COMPOUND);
        for(int i=0;i<conflicts.size();i++){
            State state;
            if(modern)state=readState(conflicts.getCompound(i),registries);
            else {var fluid=FluidStack.parseOptional(registries,conflicts.getCompound(i));state=new State(fluid,CultureFouling.dirty(fluid,CultureProfiles.wasteFormulaMb()),fluid.getAmount(),0);}
            if(state.total()>0)reconnectionConflicts.add(state);
        }
    }
    @Override public CompoundTag writeToNBT(HolderLookup.Provider registries,CompoundTag tag){tag.merge(save(registries));return tag;}
    @Override public FluidTank readFromNBT(HolderLookup.Provider registries,CompoundTag tag){load(tag,registries);return this;}
    /** Capture all sources before removal; mixed identities remain in the world for correction. */
    public void migrate(Level level){
        if(migrated || !isEmpty())return;
        var sources=new ArrayList<BlockPos>();var liquids=new ArrayList<BlockPos>();FluidStack captured=FluidStack.EMPTY;
        for(var p:cells.keySet().stream().sorted(VatTopologyCompiler.POSITION).toList()){
            var state=level.getBlockState(p);var fluid=state.getFluidState();
            if(!(state.getBlock() instanceof LiquidBlock) || fluid.isEmpty())continue;
            liquids.add(p);if(!fluid.isSource())continue;
            var sample=new FluidStack(fluid.getType(),1000);
            if(!captured.isEmpty() && !FluidStack.isSameFluidSameComponents(captured,sample)){
                migrationDiagnostic="Legacy vat medium conflict @ "+p.toShortString()+"; source blocks retained";changed.run();return;
            }
            captured=sample;sources.add(p);
        }
        if(!sources.isEmpty()){
            for(var p:liquids)if(!level.getFluidState(p).getType().isSame(captured.getFluid())){
                migrationDiagnostic="Legacy vat medium conflict @ "+p.toShortString()+"; source blocks retained";changed.run();return;
            }
            setFluid(captured.copyWithAmount(Math.multiplyExact(sources.size(),1000)));
            for(var p:liquids)level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
        }
        migrated=true;migrationDiagnostic="";changed.run();
    }
    /** Reconnection normally imports an empty fragment. Incompatible stores are never blended. */
    public void assimilate(VatMediumStorage source){
        if(isEmpty() && !source.isEmpty())restore(source.snapshot());
        else if(!source.isEmpty()){
            if(FluidStack.matches(cleanPrototype,source.cleanPrototype) && FluidStack.matches(dirtyPrototype,source.dirtyPrototype)){
                int clean=Math.addExact(cleanAmount,source.cleanAmount),dirty=Math.addExact(dirtyAmount,source.dirtyAmount);Math.addExact(clean,dirty);cleanAmount=clean;dirtyAmount=dirty;
            }else reconnectionConflicts.add(source.snapshot());
        }
        source.reconnectionConflicts.forEach(s->reconnectionConflicts.add(new State(s.clean(),s.dirty(),s.cleanAmount(),s.dirtyAmount())));
        if(migrationDiagnostic.isEmpty())migrationDiagnostic=source.migrationDiagnostic;
        migrated|=source.migrated;changed.run();
    }
}
