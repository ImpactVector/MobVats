package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** Resource metadata and legacy migration escrow; assembled machines store fluids in physical blocks. */
public final class ProductBuffers implements IFluidHandler {
    public static final ResourceLocation MILK=id("milk"), MEAT=id("meat"), WASTE=id("waste"),
        CULTURE=id("culture_slurry"), FEEDING=id("feeding_slurry");
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("mobvats",path);}
    private static final Map<String,ResourceLocation> LEGACY=Map.of("MILK",MILK,"MEAT",MEAT,"WASTE",WASTE,"CULTURE",CULTURE,"FEEDING",FEEDING);
    public static ResourceLocation key(String value){var legacy=LEGACY.get(value);return legacy==null?ResourceLocation.parse(value):legacy;}
    public static Map<ResourceLocation,ProductBufferDefinition> builtins(){
        var result=new LinkedHashMap<ResourceLocation,ProductBufferDefinition>();
        result.put(MILK,new ProductBufferDefinition(List.of("minecraft:milk"),4000,true));
        result.put(MEAT,new ProductBufferDefinition(List.of("mobvats:meat"),4000,false));
        result.put(WASTE,new ProductBufferDefinition(List.of("mobvats:waste"),4000,false));
        result.put(CULTURE,new ProductBufferDefinition(List.of("mobvats:culture_slurry"),4000,true));
        result.put(FEEDING,new ProductBufferDefinition(List.of("mobvats:feeding_slurry"),4000,true));return result;
    }
    private static final class Entry {
        ProductBufferDefinition definition; Predicate<FluidStack> selector; boolean active=true;
        final FluidTank tank;
        Entry(ProductBufferDefinition definition,FluidTank supplied,Runnable changed){
            this.definition=definition;selector=definition.selector();
            tank=supplied==null?new FluidTank(definition.limit(),s->active && selector.test(s)){
                protected void onContentsChanged(){changed.run();}
            }:supplied;
            tank.setValidator(s->active && selector.test(s));tank.setCapacity(definition.limit());
        }
        boolean compatible(){return active && (tank.isEmpty() || selector.test(tank.getFluid()));}
    }
    private final NavigableMap<ResourceLocation,Entry> entries=new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
    private List<Entry> ordered=List.of();
    private List<ResourceLocation> orderedIds=List.of();
    private final Runnable changed;
    private final Set<String> conflicts=new TreeSet<>();
    private ResourceLocation cursor;
    private net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage physical;
    public void bind(net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage storage){physical=storage;migrate();}
    /** Retry only persisted legacy escrow; new process output can never enter these entries. */
    public boolean migrate(){
        if(physical==null)return false;boolean movedAny=false;
        var handler=net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.handler(physical.clusters().stream().map(net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.Cluster::tank).toList(),true,s->true);
        for(var e:entries.values()){e.tank.setCapacity(0);if(!e.tank.isEmpty()){int moved=handler.fill(e.tank.getFluid(),FluidAction.EXECUTE);e.tank.drain(moved,FluidAction.EXECUTE);movedAny|=moved>0;}}
        return movedAny;
    }
    public boolean physical(){return physical!=null;}
    private FluidTank physicalTank(ResourceLocation id){var e=entries.get(id);if(e==null || !e.active)return null;var tanks=physical.clusters().stream().map(net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.Cluster::tank).filter(t->!t.isEmpty() && e.selector.test(t.getFluid())).toList();if(!tanks.isEmpty())return tanks.getFirst();for(var c:physical.clusters())if(c.tank().isEmpty())for(var selector:e.definition.fluids())if(!selector.startsWith("#")){var fluid=new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse(selector)),1);if(c.tank().isFluidValid(fluid))return c.tank();}return null;}
    public ProductBuffers(Runnable changed){this(null,changed);}
    public ProductBuffers(FluidTank milk,Runnable changed){
        this.changed=changed;builtins().forEach((id,d)->entries.put(id,new Entry(d,id.equals(MILK)?milk:null,changed)));refresh();
    }
    private void refresh(){ordered=List.copyOf(entries.values());orderedIds=List.copyOf(entries.keySet());}
    public List<ResourceLocation> ids(){return orderedIds;}
    public FluidTank buffer(ResourceLocation id){if(physical!=null){var tank=physicalTank(id);if(tank!=null)return tank;return new FluidTank(0);}
        var entry=entries.get(id);if(entry==null)throw new IllegalArgumentException("Undefined buffer: "+id);return entry.tank;}
    public FluidTank find(ResourceLocation id){if(physical!=null)return physicalTank(id);var entry=entries.get(id);return entry==null?null:entry.tank;}
    public boolean compatible(ResourceLocation id){if(physical!=null)return physicalTank(id)!=null;var entry=entries.get(id);return entry!=null && entry.compatible();}
    public void definitionConflict(ResourceLocation id){conflicts.add("Conflicting specimen buffer definitions: "+id+"; stable first Head definition retained");}
    public void configure(Map<ResourceLocation,ProductBufferDefinition> definitions){
        entries.values().forEach(e->e.active=false);
        entries.entrySet().removeIf(e->!definitions.containsKey(e.getKey()) && e.getValue().tank.isEmpty());
        definitions.forEach((id,d)->{var e=entries.get(id);if(e==null)entries.put(id,new Entry(d,null,changed));
            else {e.definition=d;e.selector=d.selector();e.active=true;e.tank.setCapacity(physical==null?d.limit():0);}});
        refresh();changed.run();
    }
    public List<String> diagnostics(){
        var result=new ArrayList<>(conflicts);
        if(physical!=null)entries.forEach((id,e)->{if(!e.tank.isEmpty())result.add("Legacy migration pending: "+id+" / "+e.tank.getFluidAmount()+" mB; provide compatible physical Fluid Buffer capacity");});
        entries.forEach((id,e)->{if(!e.tank.isEmpty() && !e.compatible())result.add("Buffer "+id+": stored fluid incompatible with current definition (contents preserved)");});
        return List.copyOf(result);
    }
    public String status(){return entries.entrySet().stream().filter(e->e.getValue().active || !e.getValue().tank.isEmpty()).map(e->{var t=e.getValue().tank;return e.getKey()+" ["+(t.isEmpty()?"empty":BuiltInRegistries.FLUID.getKey(t.getFluid().getFluid()))+"] "+t.getFluidAmount()+"/"+t.getCapacity()+" mB";}).collect(java.util.stream.Collectors.joining(" | "))+String.join("; ",diagnostics());}
    private FluidTank tank(int index){return physical==null?ordered.get(index).tank:physical.clusters().get(index).tank();}
    public int getTanks(){return physical==null?ordered.size():physical.clusters().size();}
    public FluidStack getFluidInTank(int index){return tank(index).getFluid().copy();}
    public int getTankCapacity(int index){return tank(index).getCapacity();}
    public boolean isFluidValid(int index,FluidStack stack){return (physical!=null || ordered.get(index).compatible()) && tank(index).isFluidValid(stack);}
    public int fill(FluidStack stack,FluidAction action){return 0;}
    public IFluidHandler inputHandler(){if(physical!=null)return net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.handler(physical.clusters().stream().map(net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.Cluster::tank).toList(),true,s->true);return new IFluidHandler(){
        public int getTanks(){return ProductBuffers.this.getTanks();}
        public FluidStack getFluidInTank(int i){return ProductBuffers.this.getFluidInTank(i);}
        public int getTankCapacity(int i){return ProductBuffers.this.getTankCapacity(i);}
        public boolean isFluidValid(int i,FluidStack s){return ordered.get(i).definition.input() && ProductBuffers.this.isFluidValid(i,s);}
        public int fill(FluidStack s,FluidAction a){for(var e:ordered)if(e.definition.input() && e.compatible() && e.tank.isFluidValid(s) && e.tank.getFluidAmount()<e.tank.getCapacity()){int filled=e.tank.fill(s,a);if(filled>0)return filled;}return 0;}
        public FluidStack drain(FluidStack s,FluidAction a){return FluidStack.EMPTY;}
        public FluidStack drain(int n,FluidAction a){return FluidStack.EMPTY;}
    };}
    public FluidStack drain(FluidStack requested,FluidAction action){
        if(physical!=null)return net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.handler(physical.clusters().stream().map(net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.Cluster::tank).toList(),false,s->true).drain(requested,action);
        if(requested.isEmpty())return FluidStack.EMPTY;
        for(var entry:entries.entrySet())if(FluidStack.isSameFluidSameComponents(entry.getValue().tank.getFluid(),requested))return take(entry.getKey(),requested.getAmount(),action);
        return FluidStack.EMPTY;
    }
    public FluidStack drain(int amount,FluidAction action){
        if(physical!=null)return net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.handler(physical.clusters().stream().map(net.impactvector.mobvats.gamecontent.multiblock.conduit.PhysicalFluidStorage.Cluster::tank).toList(),false,s->true).drain(amount,action);
        if(amount<=0 || entries.isEmpty())return FluidStack.EMPTY;
        var ids=ids();int start=cursor==null?0:ids.indexOf(cursor);if(start<0)start=0;
        for(int offset=0;offset<ids.size();offset++){var id=ids.get((start+offset)%ids.size());if(!buffer(id).isEmpty())return take(id,amount,action);}return FluidStack.EMPTY;
    }
    private FluidStack take(ResourceLocation id,int amount,FluidAction action){
        var result=buffer(id).drain(amount,action);if(action.execute() && !result.isEmpty()){var next=entries.higherKey(id);cursor=next==null?entries.firstKey():next;changed.run();}return result;
    }
    public CompoundTag save(HolderLookup.Provider registries){
        var tag=new CompoundTag();var buffers=new CompoundTag();
        entries.forEach((id,e)->{var state=new CompoundTag();state.put("tank",e.tank.writeToNBT(registries,new CompoundTag()));state.putString("definition",PassiveMobDefinitionRegistry.JSON.toJson(e.definition));state.putBoolean("active",e.active);buffers.put(id.toString(),state);});
        tag.put("buffers",buffers);if(cursor!=null)tag.putString("cursor",cursor.toString());var issues=new ListTag();conflicts.forEach(s->issues.add(StringTag.valueOf(s)));tag.put("conflicts",issues);return tag;
    }
    public void load(CompoundTag tag,HolderLookup.Provider registries){
        clear();
        if(tag.contains("buffers")){
            entries.clear();var buffers=tag.getCompound("buffers");
            for(var name:buffers.getAllKeys()){
                var id=ResourceLocation.parse(name);var state=buffers.getCompound(name);
                var definition=PassiveMobDefinitionRegistry.JSON.fromJson(state.getString("definition"),ProductBufferDefinition.class);
                var e=new Entry(definition,null,changed);e.active=state.getBoolean("active");e.tank.readFromNBT(registries,state.getCompound("tank"));entries.put(id,e);
            }
            cursor=ResourceLocation.tryParse(tag.getString("cursor"));
            var issues=tag.getList("conflicts",Tag.TAG_STRING);for(int i=0;i<issues.size();i++)conflicts.add(issues.getString(i));
        }else {
            entries.clear();builtins().forEach((id,d)->entries.put(id,new Entry(d,null,changed)));
            for(var name:List.of("meat","waste","culture","feeding","milk"))if(tag.contains(name))entries.get(LEGACY.get(name.toUpperCase(Locale.ROOT))).tank.readFromNBT(registries,tag.getCompound(name));
        }
        refresh();
    }
    public void loadLegacyMilk(CompoundTag tag,HolderLookup.Provider registries){entries.get(MILK).tank.readFromNBT(registries,tag);}
    public void assimilate(ProductBuffers source){
        source.entries.forEach((id,e)->{
            if(e.tank.isEmpty())return;
            var destination=entries.computeIfAbsent(id,k->{var copy=new Entry(e.definition,null,changed);copy.active=e.active;return copy;});
            if(!destination.definition.equals(e.definition))conflicts.add("Conflicting buffer definitions: "+id);
            if(!destination.tank.isEmpty() && !FluidStack.isSameFluidSameComponents(destination.tank.getFluid(),e.tank.getFluid())){
                // Keep both fluids under separate stable storage keys; the retained tank is export-only.
                int n=1;ResourceLocation retained;do{retained=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),id.getPath()+"/retained_"+n++);}while(entries.containsKey(retained));
                var copy=new Entry(e.definition,null,changed);copy.active=false;copy.tank.setFluid(e.tank.getFluid().copy());entries.put(retained,copy);
                conflicts.add("Conflicting stored fluids for buffer "+id+"; preserved in "+retained);
            }else {var contents=e.tank.getFluid().copy();contents.setAmount(Math.addExact(destination.tank.getFluidAmount(),contents.getAmount()));destination.tank.setFluid(contents);}
        });refresh();changed.run();
    }
    public void clear(){entries.values().forEach(e->e.tank.setFluid(FluidStack.EMPTY));cursor=null;conflicts.clear();}
    private int amount(ResourceLocation id){if(physical!=null){var e=entries.get(id);return e==null?0:physical.clusters().stream().mapToInt(c->e.selector.test(c.tank().getFluid())?c.tank().getFluidAmount():0).sum();}var entry=entries.get(id);return entry==null?0:entry.tank.getFluidAmount();}
    public int meatAmount(){return amount(MEAT);}public int wasteAmount(){return amount(WASTE);}
    public int cultureAmount(){return amount(CULTURE);}public int feedingAmount(){return amount(FEEDING);}
    public boolean consumeCulture(int n){return consume(CULTURE,n);}public boolean consumeFeeding(int n){return consume(FEEDING,n);}
    private boolean consume(ResourceLocation id,int n){
        if(n<=0 || amount(id)<n)return false;
        if(physical==null)return buffer(id).drain(n,FluidAction.EXECUTE).getAmount()==n;
        var transaction=new ProcessTransaction();int remaining=n;var entry=entries.get(id);
        for(var cluster:physical.clusters()){var tank=cluster.tank();if(!entry.selector.test(tank.getFluid()))continue;int used=Math.min(remaining,tank.getFluidAmount());var after=tank.getFluid().copy();after.shrink(used);transaction.add(ProcessTransaction.fluid(tank,after));remaining-=used;if(remaining==0)break;}
        return remaining==0 && transaction.commit();
    }
    public boolean produceMeat(){return produce(MEAT,"mobvats:meat",CowBalance.MEAT_PER_HARVEST);}
    public boolean produceWaste(){return produce(WASTE,"mobvats:waste",CowBalance.WASTE_PER_INTERVAL);}
    private boolean produce(ResourceLocation id,String fluid,int n){var tank=find(id);if(tank==null)return false;var step=ProcessOutput.fluid(tank,new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse(fluid)),n));return step!=null && new ProcessTransaction().add(step).commit();}
}
