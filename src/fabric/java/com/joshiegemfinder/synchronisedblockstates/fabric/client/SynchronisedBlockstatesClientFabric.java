package com.joshiegemfinder.synchronisedblockstates.fabric.client;

import com.joshiegemfinder.synchronisedblockstates.common.client.SynchronisedBlockstatesClient;
import com.joshiegemfinder.synchronisedblockstates.common.client.handler.ChunkedRegistryHandler;
import com.joshiegemfinder.synchronisedblockstates.common.client.handler.RegistryRemapHandler;
import com.joshiegemfinder.synchronisedblockstates.fabric.mixin.client.ClientHandshakePacketListenerImplConnectionAccessor;
import com.joshiegemfinder.synchronisedblockstates.fabric.network.client.SynchronisedBlockstatesNetworkFabricClient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class SynchronisedBlockstatesClientFabric implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		SynchronisedBlockstatesClient.onInitializeClient();
		
		SynchronisedBlockstatesNetworkFabricClient.registerPackets();

		ClientLoginConnectionEvents.INIT.register((handler, client) -> {
			ChunkedRegistryHandler.clearDecoders();
			RegistryRemapHandler.onLoginPhaseBegan(((ClientHandshakePacketListenerImplConnectionAccessor)handler).getConnection().isMemoryConnection());
		});
		
		ClientPlayConnectionEvents.INIT.register((handler, client) -> {
			RegistryRemapHandler.onLoginPhaseEnd(handler.getConnection().isMemoryConnection());
		});
	}
}
