package net.impactvector.mobvats.gamecontent.multiblock.common;
/** Implemented by machine-specific adapters; shared state has no machine superclass. */
public interface ConfigurableEndpoint extends it.zerono.mods.zerocore.lib.data.IIoEntity {
    EndpointConfiguration endpoint();
    default boolean boundaryAvailable(){return true;}
    default String filter(){return endpoint().filter();}
    default void filter(String value){endpoint().filter(value);}
    default it.zerono.mods.zerocore.lib.data.IoDirection getIoDirection(){return endpoint().direction();}
    default void setIoDirection(it.zerono.mods.zerocore.lib.data.IoDirection value){endpoint().direction(value);}
    default it.zerono.mods.zerocore.lib.data.IoMode getIoMode(){return endpoint().mode();}
    default void setIoMode(it.zerono.mods.zerocore.lib.data.IoMode value){endpoint().mode(value);}
}
