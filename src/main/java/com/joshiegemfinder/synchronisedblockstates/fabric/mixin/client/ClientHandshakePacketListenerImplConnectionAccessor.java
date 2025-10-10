package com.joshiegemfinder.synchronisedblockstates.fabric.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;

// one of the java class names of all time
@Mixin(ClientHandshakePacketListenerImpl.class)
public interface ClientHandshakePacketListenerImplConnectionAccessor {
	@Accessor
	Connection getConnection();
}
