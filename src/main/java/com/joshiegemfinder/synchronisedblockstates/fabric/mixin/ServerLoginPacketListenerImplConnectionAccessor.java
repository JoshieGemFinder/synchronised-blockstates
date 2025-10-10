package com.joshiegemfinder.synchronisedblockstates.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

// one of the java class names of all time
@Mixin(ServerLoginPacketListenerImpl.class)
public interface ServerLoginPacketListenerImplConnectionAccessor {
	@Accessor
	Connection getConnection();
}
