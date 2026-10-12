package net.impactvector.benchmark;

import java.util.*;

/** Inclusive callback time, restricted to the server thread; nested values must not be summed. */
public final class Probes {
    private static final Map<String,long[]> TOTALS=new TreeMap<>();
    private static final long[] STACK=new long[256];
    private static int depth;
    public static void enter() {
        if(!Thread.currentThread().getName().equals("Server thread")) return;
        if(depth>=STACK.length) throw new IllegalStateException("Probe recursion exceeded");
        STACK[depth++]=System.nanoTime();
    }
    public static void exit(Object controller,String method) {
        if(!Thread.currentThread().getName().equals("Server thread")) return;
        long elapsed=System.nanoTime()-STACK[--depth];
        var value=TOTALS.computeIfAbsent(controller.getClass().getSimpleName()+"."+method,k->new long[2]);
        value[0]++; value[1]+=elapsed;
    }
    public static Map<String,long[]> snapshot() {
        var result=new TreeMap<String,long[]>(); TOTALS.forEach((k,v)->result.put(k,v.clone())); return result;
    }
    public static Map<String,Object> difference(Map<String,long[]> before) {
        var result=new TreeMap<String,Object>();
        TOTALS.forEach((k,v)->{ var old=before.getOrDefault(k,new long[2]);
            if(v[0]!=old[0]) result.put(k,Map.of("calls",v[0]-old[0],"inclusive_ms",(v[1]-old[1])/1e6)); });
        return result;
    }
}
