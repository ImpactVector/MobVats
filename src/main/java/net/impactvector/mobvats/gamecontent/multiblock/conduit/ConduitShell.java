package net.impactvector.mobvats.gamecontent.multiblock.conduit;

/** Structural identity is independent of resource, cladding, and TRUNK/TAP mode. */
public enum ConduitShell {
    SHARED, VAT, MIXER;

    public static ConduitShell forController(Class<?> type){
        if(type==net.impactvector.mobvats.gamecontent.multiblock.vat.MultiblockVat.class)return VAT;
        if(type==net.impactvector.mobvats.gamecontent.multiblock.mixer.MultiblockMixer.class)return MIXER;
        return SHARED;
    }
    public static ConduitShell normalize(ConduitCladding cladding,ConduitShell shell){
        if(cladding==ConduitCladding.BARE)return SHARED;
        if(shell==SHARED)throw new IllegalArgumentException("Cladded blocks require a machine shell family");
        return shell;
    }
}
