package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;

public final class SpecimenProcessState {
    private final YieldRemainders yield = new YieldRemainders();
    private final FoulingRemainders fouling = new FoulingRemainders();

    public FoulingRemainders fouling() {
        return fouling;
    }

    public YieldRemainders yield() {
        return yield;
    }

    private LifeStage stage = LifeStage.ADULT;
    private String feedSource = "None";

    public String feedSource() {
        return feedSource;
    }

    public void feedSource(String value) {
        feedSource = Objects.requireNonNull(value);
    }

    public LifeStage stage() {
        return stage;
    }

    public void stage(LifeStage value) {
        stage = Objects.requireNonNull(value);
    }

    public ProcessState growth() {
        return process(PassiveProcessCompiler.GROWTH);
    }

    private final Map<String, ProcessState> processes = new TreeMap<>();

    ProcessState existingProcess(String id) {
        return processes.get(id);
    }

    /** Detached fallback for presentation readers; never installs missing timers. */
    public ProcessState readProcess(String id) {
        var state = processes.get(id);
        return state == null ? new ProcessState() : state;
    }

    public ProcessState process(String id) {
        return processes.computeIfAbsent(id, k -> new ProcessState());
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString("lifeStage", stage.name());
        tag.putString("feedSource", feedSource);
        tag.put("yieldRemainders", yield.save());
        tag.put("foulingRemainders", fouling.save());
        processes.forEach((id, state) -> tag.put(id, state.save(registries)));
        return tag;
    }

    public static SpecimenProcessState load(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new SpecimenProcessState();
        try {
            state.stage = LifeStage.valueOf(tag.getString("lifeStage"));
        } catch (IllegalArgumentException ignored) {
        }
        if (tag.contains("feedSource")) state.feedSource = tag.getString("feedSource");
        state.yield.load(tag.getCompound("yieldRemainders"));
        state.fouling.load(tag.getCompound("foulingRemainders"));
        for (var id : tag.getAllKeys())
            if (!id.equals("yieldRemainders") && !id.equals("foulingRemainders") && tag.contains(id, Tag.TAG_COMPOUND))
                state.processes.put(id, ProcessState.load(tag.getCompound(id), registries));
        return state;
    }
}
