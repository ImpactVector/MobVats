package net.impactvector.mobvats.gamecontent.multiblock.vat.process;
import java.util.*;

import net.minecraft.resources.ResourceLocation;
import net.impactvector.mobvats.gamecontent.multiblock.vat.MultiblockVat;
/** Species lookup is performed on topology rebuild, never across unrelated definitions each tick. */
public final class MobProcessRegistry {
    public static final class Profile {
        private final List<ProcessDefinition> definitions,tick,cadence;
        private final Map<ResourceLocation,ProductBufferDefinition> buffers;
        public Map<ResourceLocation,ProductBufferDefinition> buffers(){return buffers;}
        public Profile(List<ProcessDefinition> definitions){
            this(definitions,null);
        }
        public Profile(List<ProcessDefinition> definitions,Map<ResourceLocation,ProductBufferDefinition> buffers){
            this.buffers=buffers==null?null:Map.copyOf(buffers);
            this.definitions=definitions.stream().sorted(Comparator.comparing(ProcessDefinition::id)).toList();
            if(this.definitions.stream().map(ProcessDefinition::id).distinct().count()!=definitions.size())throw new IllegalArgumentException("Duplicate process id");
            tick=this.definitions.stream().filter(d->d.stage()==ProcessDefinition.Stage.TICK).toList();
            cadence=this.definitions.stream().filter(d->d.stage()==ProcessDefinition.Stage.CADENCE).toList();
        }
        public List<ProcessDefinition> definitions(){return definitions;}
        public List<ProcessDefinition> definitions(ProcessDefinition.Stage stage){return stage==ProcessDefinition.Stage.TICK?tick:cadence;}
    }
    private final Map<ResourceLocation,Profile> profiles=new HashMap<>();
    private boolean frozen;
    public void register(ResourceLocation mob,Profile profile){if(frozen)throw new IllegalStateException("Frozen registry");if(profiles.putIfAbsent(mob,profile)!=null)throw new IllegalArgumentException("Duplicate mob");}
    public MobProcessRegistry freeze(){frozen=true;return this;}
    public Profile resolve(ResourceLocation mob){return profiles.get(mob);}
    private long revision;
    public long revision(){return revision;}
    void replace(Map<ResourceLocation,Profile> loaded){profiles.clear();profiles.putAll(loaded);++revision;}
    public static final MobProcessRegistry DEFAULT=new MobProcessRegistry().freeze();
}
