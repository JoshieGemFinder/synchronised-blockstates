package com.joshiegemfinder.synchronisedblockstates.fabric.event;

import java.util.function.Consumer;

import com.joshiegemfinder.synchronisedblockstates.common.network.login.QueryIdGenerator;
import com.joshiegemfinder.synchronisedblockstates.common.network.login.Task;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

public class CollectLoginTasksEvent {
	
	public static final Event<LoginTaskCollector> EVENT = EventFactory.createArrayBacked(LoginTaskCollector.class,
			(listeners) -> (ServerLoginPacketListenerImpl packetListener, MinecraftServer server, GameProfile profile, QueryIdGenerator queryIdGenerator, Consumer<Task> taskConsumer) -> {
				for(LoginTaskCollector listener : listeners) {
					listener.collectTasks(packetListener, server, profile, queryIdGenerator, taskConsumer);
				}
			}
		);
	
	@FunctionalInterface
	public interface LoginTaskCollector {
		/**
		 * Collect the tasks to run before the player logs in.
		 * @param packetListener the packet listener to register the tasks for
		 * @param server The {@linkplain MinecraftServer} the player will be connecting to
		 * @param profile The player's {@linkplain GameProfile}
		 * @param queryIdGenerator Generator for transaction ids
		 * @param taskConsumer Consumer to put your tasks into
		 */
		void collectTasks(ServerLoginPacketListenerImpl packetListener, MinecraftServer server, GameProfile profile, QueryIdGenerator queryIdGenerator, Consumer<Task> taskConsumer);
	}
	
}
