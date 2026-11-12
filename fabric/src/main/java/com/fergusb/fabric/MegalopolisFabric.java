package com.fergusb.fabric;

import com.fergusb.Megalopolis;
import net.fabricmc.api.ModInitializer;

public final class MegalopolisFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		Megalopolis.init(new FabricPlatform());
	}
}
