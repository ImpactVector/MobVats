package net.impactvector.benchmark;

import jdk.jfr.*;

@Name("mobvats.benchmark.Window")
@Label("Lifecycle benchmark window")
@Category("Lifecycle benchmark")
public final class BenchmarkWindow extends Event {
    public String implementation;
    public String machine;
    public String scenario;
    public int count;
    public int repetition;
}
