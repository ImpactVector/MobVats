package net.impactvector.mobvats.gamecontent.multiblock.conduit;
import java.util.*;
import net.impactvector.mobvats.gamecontent.Content;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
/** Shared bare internals and machine-specific cladding; no entity registrations. */
public final class ConduitContent {
    private static final Map<String,DeferredBlock<Block>> BLOCKS=new LinkedHashMap<>();
    private static final Map<String,DeferredItem<ConduitBlockItem>> ITEMS=new LinkedHashMap<>();
    static {
        for(var resource:ConduitResource.values()){
            String name=name(resource);
            var block=Content.BLOCKS.<Block>register(name,()->new ConduitBlock(resource));
            BLOCKS.put(name,block);
            for(var mode:ConduitMode.values()) {
                String itemName=name+(mode==ConduitMode.TAP?"_tap":"");
                ITEMS.put(itemName,Content.ITEMS.register(itemName,()->new ConduitBlockItem(block.get(),mode)));
            }
        }
    }
    public static String name(ConduitResource resource){return resource.name().toLowerCase(Locale.ROOT)+"_conduit";}
    public static Block block(ConduitResource resource){return BLOCKS.get(name(resource)).get();}
    public static net.minecraft.world.level.block.state.BlockState state(ConduitResource resource,ConduitCladding cladding){return state(resource,cladding,ConduitShell.VAT);}
    public static net.minecraft.world.level.block.state.BlockState state(ConduitResource resource,ConduitCladding cladding,ConduitShell shell){return block(resource).defaultBlockState().setValue(CladdingMaterial.PROPERTY,CladdingMaterial.of(cladding,shell));}
    public static Collection<Block> blocks(){return BLOCKS.values().stream().map(DeferredBlock::get).toList();}
    public static ConduitBlockItem item(ConduitResource resource,ConduitMode mode){return ITEMS.get(name(resource)+(mode==ConduitMode.TAP?"_tap":"")).get();}
    public static Collection<Item> items(){return ITEMS.values().stream().map(i->(Item)i.get()).toList();}
    public static void initialize(){}
    private ConduitContent(){}
}
