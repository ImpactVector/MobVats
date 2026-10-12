package net.impactvector.mobvats.gamecontent.multiblock.vat.part;
import net.impactvector.mobvats.gamecontent.multiblock.vat.*;
import net.impactvector.mobvats.gamecontent.multiblock.vat.process.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
/** Last-mile actor; supply stays in connected staging endpoints and physical tanks. */
public final class VatFeedPortEntity extends AbstractVatEntity implements VatActor {
    public static final double RANGE=3;
    public VatFeedPortEntity(BlockPos pos,BlockState state){super(VatPartType.FeedPort,pos,state);}
    public int priority(){return 10;}
    public String useKey(){return PassiveUses.FEED;}
    public boolean usesLocalItemPorts(){return true;}
    public boolean compatible(MobAssembly assembly){var d=PassiveMobDefinitionRegistry.resolve(assembly.head().getIdentity());return d!=null && d.lifecycle();}
    public boolean reaches(Level level,BlockPos actor,BlockPos min,BlockPos max,MobAssembly target,java.util.List<MobAssembly> all){return VatTopologyCompiler.radialTargets(level,actor,target,all,RANGE,false);}
}
