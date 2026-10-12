package net.impactvector.benchmark.mixin;
import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
public final class ProbePlugin implements IMixinConfigPlugin {
    public void onLoad(String name) {}
    public String getRefMapperConfig() {return null;}
    public boolean shouldApplyMixin(String target,String mixin) {return Boolean.parseBoolean(System.getProperty("benchmark.probes","true"));}
    public void acceptTargets(Set<String> mine,Set<String> others) {}
    public List<String> getMixins() {return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
}
