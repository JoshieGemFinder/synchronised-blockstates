package com.joshiegemfinder.synchronisedblockstates.fabric;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.fabric.network.SynchronisedBlockstatesNetworkFabric;

import net.fabricmc.api.ModInitializer;

public class SynchronisedBlockstatesFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		SynchronisedBlockstates.onInitialize();
		
		SynchronisedBlockstatesNetworkFabric.registerPackets();
		SynchronisedBlockstatesNetworkFabric.registerTasks();
	}
}