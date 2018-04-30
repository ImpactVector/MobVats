package net.impactvector.mobvats.helpers;

import net.impactvector.mobvats.block.VatCasing;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;

public final class Properties {

    public static final PropertyEnum<MachinePartState> PARTSTATE = PropertyEnum.create("afstate", MachinePartState.class);
    public static final PropertyEnum<CasingType> CASINGTYPE = PropertyEnum.create("casing", CasingType.class);
    public static final PropertyEnum<ControllerState> CONTROLLERSTATE = PropertyEnum.create("controller", ControllerState.class);
    public static final PropertyEnum<PortDirection> PORTDIRECTION = PropertyEnum.create("portdirection", PortDirection.class);
    public static final PropertyEnum<PowerTapState> POWERTAPSTATE = PropertyEnum.create("powerstate", PowerTapState.class);
    public static final PropertyBool LIT = PropertyBool.create("lit");

    private Properties() {
    }
}