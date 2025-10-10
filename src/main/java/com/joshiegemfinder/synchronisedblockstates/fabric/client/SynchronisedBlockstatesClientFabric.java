package com.joshiegemfinder.synchronisedblockstates.fabric.client;

import com.joshiegemfinder.synchronisedblockstates.common.client.SynchronisedBlockstatesClient;
import com.joshiegemfinder.synchronisedblockstates.fabric.network.client.SynchronisedBlockstatesNetworkFabricClient;

import net.fabricmc.api.ClientModInitializer;

public class SynchronisedBlockstatesClientFabric implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		SynchronisedBlockstatesClient.onInitializeClient();
//		dumpVanillaMappings();
		
		SynchronisedBlockstatesNetworkFabricClient.registerPackets();
	}
}
