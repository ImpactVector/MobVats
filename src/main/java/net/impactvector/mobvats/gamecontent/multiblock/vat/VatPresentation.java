package net.impactvector.mobvats.gamecontent.multiblock.vat;

import java.util.*;
import java.util.stream.Stream;
import net.impactvector.mobvats.gamecontent.multiblock.vat.part.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;

/** Immutable render anchors compiled by the controller. No routes are discovered on the client. */
public record VatPresentation(List<Link> tools, List<Link> feeds, boolean operational) {
    public static final VatPresentation EMPTY = new VatPresentation(List.of(), List.of(), false);

    public record Link(BlockPos port, Vec3 base, Vec3 target, Direction inward, boolean assigned) {}

    public VatPresentation(List<Link> tools, List<Link> feeds) {
        this(tools, feeds, true);
    }

    public VatPresentation {
        tools = List.copyOf(tools);
        feeds = List.copyOf(feeds);
    }

    public static VatPresentation compile(
            MultiblockVat vat, MobAssembly assembly, Collection<AbstractVatEntity> ports) {
        var head = assembly.head().getBlockPos();
        var topology = vat.getTopology();
        var routes = Stream.concat(topology.actors().stream(), topology.toolLoading().stream())
                .toList();
        var tools = new ArrayList<Link>();
        for (var part : ports.stream()
                .sorted(Comparator.comparing(AbstractVatEntity::getBlockPos, VatTopologyCompiler.POSITION))
                .toList()) {
            if (!(part instanceof VatToolPortEntity)) continue;
            var tool = (VatToolPortEntity) part;
            var selected = routes.stream()
                    .filter(a -> a.actor().equals(part.getBlockPos())
                            && (tool.interactionHead() == null || a.head().equals(tool.interactionHead())))
                    .findFirst();
            // One physical mechanism is transported to one Head. Idle/unassigned ports use the first anchor.
            if (selected.isPresent()
                    ? !selected.get().head().equals(head)
                    : vat.getAssemblies().getFirst() != assembly) continue;
            boolean assigned = topology.operational(head)
                    && routes.stream()
                            .anyMatch(a -> a.actor().equals(part.getBlockPos())
                                    && a.head().equals(head));
            var link = link(vat, assembly, part.getBlockPos(), assigned, false);
            if (link != null) tools.add(link);
        }
        var feeds = topology.actors().stream()
                .filter(c -> c.head().equals(head)
                        && c.use().equals(net.impactvector.mobvats.gamecontent.multiblock.vat.process.PassiveUses.FEED))
                .map(c -> link(vat, assembly, c.actor(), true, true))
                .filter(Objects::nonNull)
                .toList();
        return new VatPresentation(tools, feeds, assembly.head().activationReady() && topology.operational(head));
    }

    private static Link link(MultiblockVat vat, MobAssembly assembly, BlockPos port, boolean assigned, boolean feed) {
        return VatInteractionGeometry.link(
                vat.getBoundingBox().getMin(), vat.getBoundingBox().getMax(), assembly, port, assigned, feed);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putBoolean("operational", operational);
        tag.put("tools", saveLinks(tools));
        tag.put("feeds", saveLinks(feeds));
        return tag;
    }

    private static ListTag saveLinks(List<Link> links) {
        var result = new ListTag();
        for (var link : links) {
            var tag = new CompoundTag();
            tag.putLong("port", link.port().asLong());
            tag.putByte("direction", (byte) link.inward().ordinal());
            tag.putBoolean("assigned", link.assigned());
            putVec(tag, "base", link.base());
            putVec(tag, "target", link.target());
            result.add(tag);
        }
        return result;
    }

    private static void putVec(CompoundTag tag, String key, Vec3 v) {
        tag.putDouble(key + "X", v.x);
        tag.putDouble(key + "Y", v.y);
        tag.putDouble(key + "Z", v.z);
    }

    private static Vec3 vec(CompoundTag tag, String key) {
        return new Vec3(tag.getDouble(key + "X"), tag.getDouble(key + "Y"), tag.getDouble(key + "Z"));
    }

    public static VatPresentation load(CompoundTag tag) {
        return new VatPresentation(
                loadLinks(tag.getList("tools", Tag.TAG_COMPOUND)),
                loadLinks(tag.getList("feeds", Tag.TAG_COMPOUND)),
                !tag.contains("operational") || tag.getBoolean("operational"));
    }

    private static List<Link> loadLinks(ListTag tags) {
        var result = new ArrayList<Link>();
        for (int i = 0; i < tags.size(); ++i) {
            var tag = tags.getCompound(i);
            var base = vec(tag, "base");
            var target = vec(tag, "target");
            if (!Double.isFinite(base.lengthSqr()) || !Double.isFinite(target.lengthSqr())) continue;
            result.add(new Link(
                    BlockPos.of(tag.getLong("port")),
                    base,
                    target,
                    Direction.from3DDataValue(tag.getByte("direction")),
                    tag.getBoolean("assigned")));
        }
        return List.copyOf(result);
    }
}
