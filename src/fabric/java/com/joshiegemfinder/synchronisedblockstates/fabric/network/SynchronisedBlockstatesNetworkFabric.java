package com.joshiegemfinder.synchronisedblockstates.fabric.network;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.network.login.TaskManagerGetter;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryBlockInfoPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryCompletePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryPropertyClassTablePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryPropertyPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryStartPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryStringTablePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.LoginTaskProbePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.UnchunkedBlockRegistryPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.task.SyncBlockstatesTask;
import com.joshiegemfinder.synchronisedblockstates.fabric.event.CollectLoginTasksEvent;
import com.joshiegemfinder.synchronisedblockstates.fabric.mixin.ServerLoginPacketListenerImplConnectionAccessor;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking.LoginSynchronizer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

public class SynchronisedBlockstatesNetworkFabric {


	public static void registerTasks() {
		CollectLoginTasksEvent.EVENT.register((packetListener, server, profile, queryIdGenerator, taskConsumer) -> {
			// if it's a memory connection, that means we're sharing the same static Block.BLOCK_STATE_REGISTRY instance anyways
			boolean isMemoryConnection = ((ServerLoginPacketListenerImplConnectionAccessor)packetListener).getConnection().isMemoryConnection();
			if(!isMemoryConnection) {
				taskConsumer.accept(new SyncBlockstatesTask(queryIdGenerator));
			}
		});
	}
	
	public static void taskDataResponse(MinecraftServer server, ServerLoginPacketListenerImpl handler, boolean understood, FriendlyByteBuf buf, LoginSynchronizer synchronizer, PacketSender responseSender) {
		if(!(handler instanceof TaskManagerGetter taskManagerGetter)) {
			return;
		}
		
		SyncBlockstatesTask task = taskManagerGetter.getCurrentTask(SyncBlockstatesTask.TYPE);
		
		if(task != null) {
			task.handleChunkedRegistryDataResponse(responseSender::sendPacket);
		}
	}
	
	public static void registerPackets() {
		ServerLoginNetworking.registerGlobalReceiver(LoginTaskProbePacket.TYPE, (server, handler, understood, buf, synchronizer, responseSender) -> {
			LoginTaskProbePacket packet = understood ? LoginTaskProbePacket.decode(buf) : null;
			
			if(packet == null) {
				SynchronisedBlockstates.LOGGER.warn("Connecting client {} is not running synchronised blockstates", handler.getUserName());
			} else if(packet.networkVersion() != SynchronisedBlockstates.NETWORK_VERSION) {
				SynchronisedBlockstates.LOGGER.warn("Connecting client {} is running network version {}, but connecting to a server running network version {}", handler.getUserName(), packet.networkVersion(), SynchronisedBlockstates.NETWORK_VERSION);
			}
			
			if(!(handler instanceof TaskManagerGetter taskManagerGetter)) {
				return;
			}
			
			if(!understood || packet == null) {
				// they are not running synchronised blockstates, don't send them the full state registry because they'll just discard it
				taskManagerGetter.completeTask(SyncBlockstatesTask.TYPE);
			} else {
				SyncBlockstatesTask task = taskManagerGetter.getCurrentTask(SyncBlockstatesTask.TYPE);
				
				if(task != null) {
					task.sendRegistry(responseSender::sendPacket);
				}
			}
		});

		ServerLoginNetworking.registerGlobalReceiver(UnchunkedBlockRegistryPacket.TYPE, (server, handler, understood, buf, synchronizer, responseSender) -> {
			if(understood) {
				String response = buf.readUtf();
				SynchronisedBlockstates.LOGGER.info("Unchunked registry response from client: {}", response);
			}
			
			if(handler instanceof TaskManagerGetter taskManagerGetter) {
				SynchronisedBlockstates.LOGGER.info("Completing task for unchunked registry");
				taskManagerGetter.completeTask(SyncBlockstatesTask.TYPE);
			}
		});

		ServerLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryStartPacket.TYPE, (server, handler, understood, buf, synchronizer, responseSender) -> {
			boolean canSend = false;
			if(understood) {
				canSend = buf.readBoolean();
			}
			
			if(!(handler instanceof TaskManagerGetter taskManagerGetter)) {
				return;
			}
			
			if(!canSend) {
				taskManagerGetter.completeTask(SyncBlockstatesTask.TYPE);
				return;
			}
			
			SyncBlockstatesTask task = taskManagerGetter.getCurrentTask(SyncBlockstatesTask.TYPE);
			
			if(task != null) {
				task.sendChunkedRegistryData(responseSender::sendPacket);
			}
		});

		ServerLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryPropertyClassTablePacket.TYPE, SynchronisedBlockstatesNetworkFabric::taskDataResponse);
		
		ServerLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryStringTablePacket.TYPE, SynchronisedBlockstatesNetworkFabric::taskDataResponse);

		ServerLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryPropertyPacket.TYPE, SynchronisedBlockstatesNetworkFabric::taskDataResponse);

		ServerLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryBlockInfoPacket.TYPE, SynchronisedBlockstatesNetworkFabric::taskDataResponse);

		ServerLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryCompletePacket.TYPE, (server, handler, understood, buf, synchronizer, responseSender) -> {
			String response = buf.readUtf();
			SynchronisedBlockstates.LOGGER.info("Chunked registry response from client: {}", response);
			
			if(handler instanceof TaskManagerGetter taskManagerGetter) {
				SynchronisedBlockstates.LOGGER.info("Completing task for chunked registry");
				taskManagerGetter.completeTask(SyncBlockstatesTask.TYPE);
			}
		});
	}

}
