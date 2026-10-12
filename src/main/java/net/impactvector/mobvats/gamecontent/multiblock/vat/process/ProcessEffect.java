package net.impactvector.mobvats.gamecontent.multiblock.vat.process;
/** Post-commit notifications and lightweight state effects; resource changes use transactions. */
@FunctionalInterface public interface ProcessEffect {
    void apply(ProcessContext context,ProcessState state);
    static ProcessEffect setStage(LifeStage stage){return(c,s)->c.state().stage(stage);}
    static ProcessEffect resetGrowth(){return(c,s)->c.state().growth().reset();}
    static ProcessEffect advanceGrowth(int ticks){return(c,s)->c.state().growth().elapsed=(int)Math.min(Integer.MAX_VALUE,(long)c.state().growth().elapsed+Math.max(0,ticks));}
    static ProcessEffect metric(String id,long amount){return(c,s)->c.metrics().record(id,amount);}
    static ProcessEffect reset(){return(c,s)->s.reset();}
    static ProcessEffect start(){return(c,s)->s.start();}
    static ProcessEffect animate(String use){return(c,s)->{for(var route:c.routes(use)){var tool=c.tool(route);if(tool!=null)c.controller().toolInteracted(tool);}};}
    static ProcessEffect addExperienceMb(long amount){return(c,s)->c.engine().addExperienceMb(amount);}
}
