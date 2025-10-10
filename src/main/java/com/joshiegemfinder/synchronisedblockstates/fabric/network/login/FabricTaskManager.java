package com.joshiegemfinder.synchronisedblockstates.fabric.network.login;

import java.util.Objects;
import java.util.function.Consumer;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.network.login.QueryIdGenerator;
import com.joshiegemfinder.synchronisedblockstates.common.network.login.TaskManager;
import com.joshiegemfinder.synchronisedblockstates.fabric.event.CollectLoginTasksEvent;
import com.joshiegemfinder.synchronisedblockstates.fabric.mixin.ServerLoginPacketListenerImplConnectionAccessor;
import com.mojang.authlib.GameProfile;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

public class FabricTaskManager extends TaskManager {

	protected final ServerLoginPacketListenerImpl packetListener;
	protected final QueryIdGenerator queryIdGenerator;
	
	public FabricTaskManager(ServerLoginPacketListenerImpl packetListener) {
		super();
		this.packetListener = Objects.requireNonNull(packetListener);
		QueryIdGenerator queryIdGenerator = null;
		try {
			queryIdGenerator = new FabricImplBasedQueryIdGenerator(packetListener);
		} catch(Throwable t) {
			SynchronisedBlockstates.LOGGER.warn("Could not extract Query ID Factory from fabric networking impl, are we behind a connector?");
			// catch Throwable to also catch ClassDefNotFound
		}
		
		if(queryIdGenerator == null) {
			queryIdGenerator = QueryIdGenerator.create();
		}
		
		this.queryIdGenerator = queryIdGenerator;
	}
	
	@Override
	protected void collectTasksInternal(MinecraftServer server, GameProfile profile) {
		CollectLoginTasksEvent.EVENT.invoker().collectTasks(this.packetListener, server, profile, this.queryIdGenerator, this.taskQueue::add);
	}

	@Override
	protected boolean isAcceptingMessages() {
		return this.packetListener.isAcceptingMessages();
	}

	@Override
	protected Consumer<Packet<?>> getPacketSender() {
		return ((ServerLoginPacketListenerImplConnectionAccessor)packetListener).getConnection()::send;
	}

	@Override
	protected void keepConnectionAlive() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void disconnect(Component component) {
		this.packetListener.disconnect(component);
	}

}
