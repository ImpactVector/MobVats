package net.impactvector.mobvats.gamecontent.multiblock.vat.process;

import net.impactvector.mobvats.gamecontent.multiblock.vat.VatMediumStorage;

/** Conversion participates in output rollback; counters are published only after successful work. */
public final class Fouling {
    public static ProcessTransaction.Step step(VatMediumStorage medium,int amount){
        var before=medium.snapshot();
        return new ProcessTransaction.Step(medium,()->medium.matches(before),()->{medium.foul(amount);return true;},()->medium.restore(before));
    }
    public static boolean commit(ProcessTransaction transaction,ProcessContext context,String action){
        return commit(transaction,context,action,null);
    }
    public static boolean commit(ProcessTransaction transaction,ProcessContext context,String action,Integer override){
        var medium=context.controller().medium();
        int amount=CultureProfiles.resolve(context.identity(),medium.cleanPrototype())==null || medium.dirtyPrototype().isEmpty()?0:Math.min(medium.cleanAmount(),override==null?CultureProfiles.foulingMb(action):Math.max(0,override));
        if(!transaction.add(step(medium,amount)).commit())return false;
        if(amount>0){context.metrics().record("cultureFouledMb",amount);context.metrics().record("foulingEvents",1);}
        return true;
    }
    public static void complete(ProcessContext context,String action){commit(new ProcessTransaction(),context,action);}
    private Fouling(){}
}
