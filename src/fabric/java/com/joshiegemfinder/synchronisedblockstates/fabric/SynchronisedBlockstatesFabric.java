package com.joshiegemfinder.synchronisedblockstates.fabric;

import com.joshiegemfinder.synchronisedblockstates.common.client.handler.ChunkedRegistryHandler;
import com.joshiegemfinder.synchronisedblockstates.common.client.handler.RegistryRemapHandler;
import com.joshiegemfinder.synchronisedblockstates.fabric.mixin.client.ClientHandshakePacketListenerImplConnectionAccessor;
import com.joshiegemfinder.synchronisedblockstates.fabric.network.SynchronisedBlockstatesNetworkFabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class SynchronisedBlockstatesFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		SynchronisedBlockstatesNetworkFabric.registerPackets();
		SynchronisedBlockstatesNetworkFabric.registerTasks();
		
		ClientLoginConnectionEvents.INIT.register((handler, client) -> {
			ChunkedRegistryHandler.clearDecoders();
			RegistryRemapHandler.onLoginPhaseBegan(((ClientHandshakePacketListenerImplConnectionAccessor)handler).getConnection().isMemoryConnection());
		});
		
		ClientPlayConnectionEvents.INIT.register((handler, client) -> {
			RegistryRemapHandler.onLoginPhaseEnd(handler.getConnection().isMemoryConnection());
		});
	}
}