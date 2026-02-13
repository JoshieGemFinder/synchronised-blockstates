package com.joshiegemfinder.synchronisedblockstates.fabric.network.client;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking.LoginQueryRequestHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

@FunctionalInterface
public interface ConfigurationQueryRequestHandler {

	/**
	 * @see LoginQueryRequestHandler#receive(Minecraft, net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl, FriendlyByteBuf, java.util.function.Consumer)
	 * @param client
	 * @param buf
	 * @return
	 */
	public @Nullable FriendlyByteBuf receive(Minecraft client, FriendlyByteBuf buf);
	
}
