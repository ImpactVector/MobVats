package net.impactvector.mobvats.gamecontent.multiblock.vat.process;
import java.util.*;
import java.util.function.*;
import net.minecraft.resources.ResourceLocation;
/** Immutable in-code data plus small typed strategies; no expression parser or event bus. */
public record ProcessDefinition(String id,String stateId,Stage stage,Predicate<ResourceLocation> specimen,
        List<ProcessRequirement> requirements,Progress progress,Backpressure backpressure,
        Operation operation,List<ProcessEffect> startEffects,List<ProcessEffect> finishEffects,String exclusiveUse) {
    public enum Stage { TICK, CADENCE }
    public enum Backpressure { NONE, OUTPUT_CAPACITY, SHARED_ITEM_BUFFER, RETAIN_TOOL_RESULT }
    public record Progress(int duration,boolean autoStart,ToDoubleFunction<ProcessContext> rate){public Progress{if(duration<1)throw new IllegalArgumentException("duration");}}
    @FunctionalInterface public interface Operation { ProcessResult run(ProcessContext context,ProcessState state); }
    public ProcessDefinition(String id,String stateId,Stage stage,Predicate<ResourceLocation> specimen,List<ProcessRequirement> requirements,Progress progress,Backpressure backpressure,Operation operation,List<ProcessEffect> startEffects,List<ProcessEffect> finishEffects){this(id,stateId,stage,specimen,requirements,progress,backpressure,operation,startEffects,finishEffects,null);}
    public ProcessDefinition withExclusiveUse(String use){return new ProcessDefinition(id,stateId,stage,specimen,requirements,progress,backpressure,operation,startEffects,finishEffects,use);}
    public ProcessDefinition{Objects.requireNonNull(id);Objects.requireNonNull(stateId);Objects.requireNonNull(stage);Objects.requireNonNull(specimen);Objects.requireNonNull(backpressure);if(progress==null)Objects.requireNonNull(operation);requirements=List.copyOf(requirements);startEffects=List.copyOf(startEffects);finishEffects=List.copyOf(finishEffects);}
    public ProcessResult execute(ProcessContext context,ProcessState state){
        if(!specimen.test(context.identity()))return ProcessResult.of(ProcessResult.Reason.SPECIMEN_INVALID);
        for(var requirement:requirements){var result=requirement.evaluate(context,state);if(result!=null)return ProcessResult.of(result);}
        if(backpressure==Backpressure.SHARED_ITEM_BUFFER && !context.controller().killOutputs().isEmpty())return ProcessResult.of(ProcessResult.Reason.BACKPRESSURE);
        if(progress!=null){
            boolean started=false;
            if(state.phase==ProcessState.Phase.IDLE && progress.autoStart()){state.start();started=true;for(var effect:startEffects)effect.apply(context,state);}
            if(state.phase!=ProcessState.Phase.RUNNING)return ProcessResult.of(state.phase==ProcessState.Phase.READY?ProcessResult.Reason.READY:state.phase==ProcessState.Phase.BLOCKED?ProcessResult.Reason.OUTPUT_BLOCKED:ProcessResult.Reason.IDLE);
            int before=state.elapsed;double remainder=state.frequencyRemainder;var previousPhase=state.phase;state.elapsed=(int)Math.min(progress.duration(),(long)before+state.advance(progress.rate().applyAsDouble(context)));
            boolean completed=state.elapsed>=progress.duration();
            if(completed){state.phase=ProcessState.Phase.READY;for(var effect:finishEffects)effect.apply(context,state);}
            return new ProcessResult(completed?ProcessResult.Reason.READY:ProcessResult.Reason.RUNNING,started || state.elapsed!=before || state.frequencyRemainder!=remainder || state.phase!=previousPhase);
        }
        var result=operation.run(context,state);if(result.reason()==ProcessResult.Reason.COMMITTED){for(var effect:startEffects)effect.apply(context,state);for(var effect:finishEffects)effect.apply(context,state);}return result;
    }
}
