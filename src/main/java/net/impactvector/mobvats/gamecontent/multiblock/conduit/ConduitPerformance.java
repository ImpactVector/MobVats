package net.impactvector.mobvats.gamecontent.multiblock.conduit;

/** Opt-in server-thread counters. No timing or logging cost in ordinary runs. */
public final class ConduitPerformance {
    public static final boolean ENABLED=Boolean.getBoolean("mobvats.conduitPerformance");
    public static long lookups, misses, components, endpoints, nanos, rebuilds, nodes, edges;
    public static long bulkQueries, buffers, enumerations, transfers, storageHits, storageMisses;
    public static String sampleAndReset(){
        var result="lookups="+lookups+" misses="+misses+" components="+components+" endpoints="+endpoints
            +" connectivityMs="+nanos/1_000_000+" rebuilds="+rebuilds+" nodes="+nodes+" edges="+edges
            +" bulkQueries="+bulkQueries+" buffers="+buffers+" enumerations="+enumerations+" transfers="+transfers
            +" storageHits="+storageHits+" storageMisses="+storageMisses;
        lookups=misses=components=endpoints=nanos=rebuilds=nodes=edges=bulkQueries=buffers=enumerations=transfers=storageHits=storageMisses=0;
        return result;
    }
    private ConduitPerformance(){}
}
