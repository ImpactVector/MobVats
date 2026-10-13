package net.impactvector.mobvats.gamecontent.multiblock.vat.process;
/** Evaluation is runtime status, not a second actor-priority system. */
public record ProcessResult(Reason reason, boolean changed) {
    public enum Reason {
        IDLE,
        RUNNING,
        READY,
        WAITING_INPUT,
        WAITING_FLUID,
        WAITING_XP,
        MISSING_ACTOR,
        MISSING_TOOL,
        OUTPUT_BLOCKED,
        BACKPRESSURE,
        SPECIMEN_INVALID,
        CONDITION_FAILED,
        EXCLUSIVE_USE,
        COMMITTED
    }

    private static final ProcessResult[] UNCHANGED = java.util.Arrays.stream(Reason.values())
            .map(r -> new ProcessResult(r, false))
            .toArray(ProcessResult[]::new);

    public static ProcessResult of(Reason reason) {
        return UNCHANGED[reason.ordinal()];
    }

    public static ProcessResult committed(boolean changed) {
        return new ProcessResult(changed ? Reason.COMMITTED : Reason.OUTPUT_BLOCKED, changed);
    }
}
