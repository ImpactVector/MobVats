package net.impactvector.mobvats;

import net.fabricmc.api.ModInitializer;
import net.impactvector.mobvats.block.ModBlocks;
import net.impactvector.mobvats.fluid.ModFluids;
import net.impactvector.mobvats.item.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobVats implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.

	public static final String MOD_ID = "mobvats";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


	@Override
	public void onInitialize() {
		ModItems.registerModItems();
		ModBlocks.registerModBlocks();
		ModFluids.registerFluids();

	}
}
