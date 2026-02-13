package com.joshiegemfinder.synchronisedblockstates.fabric.network.client;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.client.handler.ChunkedRegistryHandler;
import com.joshiegemfinder.synchronisedblockstates.common.client.handler.ClientNetworkHandler;
import com.joshiegemfinder.synchronisedblockstates.common.client.handler.RegistryRemapHandler;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryBlockInfoPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryCompletePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryPropertyClassTablePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryPropertyPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryStartPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryStringTablePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.LoginTaskProbePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.UnchunkedBlockRegistryPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.util.ClientAckResponse;
import com.joshiegemfinder.synchronisedblockstates.common.network.velocity.packet.VelocityCustomQueryPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.velocity.packet.VelocityCustomQueryResponsePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.velocity.packet.VelocityNetworkInfoPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.velocity.packet.VelocityNetworkInfoPacket.ConfigurationStatus;
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry;

import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.resources.ResourceLocation;

public class SynchronisedBlockstatesNetworkFabricClient {

	public static void registerLoginPackets() {
		// Let the server know we're running synchronised blockstates
		ClientLoginNetworking.registerGlobalReceiver(LoginTaskProbePacket.TYPE, (client, handler, buf, listenerAdder) -> {
			LoginTaskProbePacket packet = LoginTaskProbePacket.decode(buf);

			SynchronisedBlockstates.LOGGER.debug("Recieved Login Probe Packet for network version {} (client network version is {})", packet.networkVersion(), SynchronisedBlockstates.NETWORK_VERSION);
			
			ClientNetworkHandler.setServerNetworkVersion(packet.networkVersion(), packet.fallbackNetworkVersions());
			
			{
				FriendlyByteBuf buffer = PacketByteBufs.create();
				
				LoginTaskProbePacket response = new LoginTaskProbePacket();
				LoginTaskProbePacket.encode(buffer, response);
				
//				responseSender.sendPacket(LoginTaskProbePacket.TYPE, buffer);
				return CompletableFuture.completedFuture(buffer);
			}
		});
		
		// Alternative way of letting the server know we run synchronised blockstates if it's behind a velocity proxy
		ClientLoginNetworking.registerGlobalReceiver(LoginTaskProbePacket.VELOCITY_TYPE, (client, handler, buf, listenerAdder) -> {
			SynchronisedBlockstates.LOGGER.debug("Recieved Login Probe Packet for a velocity server with unknown server network version (client network version is {})", SynchronisedBlockstates.NETWORK_VERSION);

			// Tell the connection manager that we're connected to a velocity server
			ClientNetworkHandler.setVelocityMode(true);
			
			// Respond with the client's network version
			FriendlyByteBuf buffer = PacketByteBufs.create();
			
			LoginTaskProbePacket response = new LoginTaskProbePacket();
			LoginTaskProbePacket.encode(buffer, response);
			
			return CompletableFuture.completedFuture(buffer);
		});
		
	}

	public static final Map<ResourceLocation, ConfigurationQueryRequestHandler> CONFIGURATION_PACKET_HANDLERS = new ConcurrentHashMap<>();
	
	public static void registerPlayPackets() {
		ClientPlayNetworking.registerGlobalReceiver(VelocityNetworkInfoPacket.TYPE, (client, handler, buf, responseSender) -> {
			VelocityNetworkInfoPacket packet = VelocityNetworkInfoPacket.decode(buf);
			
			if(packet.configStatus() == ConfigurationStatus.BEGINNING_CONFIGURATION) {
				ClientNetworkHandler.onVelocityConfigurationStart();
			} else if(packet.configStatus() == ConfigurationStatus.ENDING_CONFIGURATION) {
				ClientNetworkHandler.onVelocityConfigurationEnd();
			}

			LoginTaskProbePacket serverNetworkVersion = packet.serverNetworkVersion();
			if(serverNetworkVersion != null) {
				ClientNetworkHandler.setServerNetworkVersion(serverNetworkVersion.networkVersion(), serverNetworkVersion.fallbackNetworkVersions());
			}
		});
		
		ClientPlayNetworking.registerGlobalReceiver(VelocityCustomQueryPacket.TYPE, (client, handler, buf, responseSender) -> {
			// Read the custom query
			VelocityCustomQueryPacket customQueryPacket = VelocityCustomQueryPacket.decode(buf);

			ResourceLocation channelId = customQueryPacket.channelId();
			int transactionId = customQueryPacket.transactionId();
			
			// Get the custom query response handler if present
			ConfigurationQueryRequestHandler configurationHandler = CONFIGURATION_PACKET_HANDLERS.get(channelId);
			
			// The packet to send back
			VelocityCustomQueryResponsePacket responsePacket;
			
			// If we understand it
			if(configurationHandler != null) {
				FriendlyByteBuf requestBuf = PacketByteBufs.create();
				requestBuf.writeBytes(customQueryPacket.data());
				@Nullable FriendlyByteBuf response = configurationHandler.receive(client, requestBuf);
				
				responsePacket = new VelocityCustomQueryResponsePacket(transactionId, response);
			} else {
				// If we don't understand it
				responsePacket = new VelocityCustomQueryResponsePacket(transactionId, (byte[])null);
			}
			
			FriendlyByteBuf responseBuf = PacketByteBufs.create();
			
			VelocityCustomQueryResponsePacket.encode(buf, responsePacket);
			
			responseSender.sendPacket(VelocityCustomQueryResponsePacket.TYPE, responseBuf, (PacketSendListener)null);
		});
	}
	
	public static void registerConfigurationPacket(ResourceLocation channelName, ConfigurationQueryRequestHandler handler) {
		// Register the packet to handle itself in the LOGIN phase
		ClientLoginNetworking.registerGlobalReceiver(channelName, (client, _handler, buf, _listenerAdder) -> {
			FriendlyByteBuf responseBuf = handler.receive(client, buf);

			return CompletableFuture.completedFuture(responseBuf);
		});
		
		CONFIGURATION_PACKET_HANDLERS.put(channelName, handler);
	}
	
	public static void registerConfigurationPackets() {

		// Handle an unchunked block registry packet
		registerConfigurationPacket(UnchunkedBlockRegistryPacket.TYPE, (client, buf) -> {
			UnchunkedBlockRegistryPacket packet = UnchunkedBlockRegistryPacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved unchunked blockstate registry from server...");
			SynchronisedBlockstates.LOGGER.info("Blockstate registry: {}", packet.registry());
			
			ClientAckResponse response = RegistryRemapHandler.handleRegistryReceived(packet.registry());
			
			FriendlyByteBuf responseBuf = PacketByteBufs.create();
			responseBuf.writeUtf(response.getSerializedName());
			return responseBuf;
		});
		

		// Handle chunked block registry packets
		registerConfigurationPacket(ChunkedBlockRegistryStartPacket.TYPE, (client, buf) -> {
			ChunkedBlockRegistryStartPacket packet = ChunkedBlockRegistryStartPacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunking start packet from server [UUID = {}]...", packet.uuid());
			
			boolean decodeYes = ChunkedRegistryHandler.startDecoding(packet.uuid(), packet.classTableSize(), packet.stringTableSize(), packet.totalPropertyCount(), packet.totalBlockCount());
			
			FriendlyByteBuf responseBuf = PacketByteBufs.create();
			responseBuf.writeBoolean(decodeYes);
			return responseBuf;
		});

		// Decode property class string table
		registerConfigurationPacket(ChunkedBlockRegistryPropertyClassTablePacket.TYPE, (client, buf) -> {
			ChunkedBlockRegistryPropertyClassTablePacket packet = ChunkedBlockRegistryPropertyClassTablePacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunked class table packet from server [UUID = {}]...", packet.uuid());
			
			ChunkedRegistryHandler.acceptPropertyClasses(packet.uuid(), packet.tableOffset(), packet.tableChunk());
			
			return PacketByteBufs.empty();
		});

		// Decode property name/value string table
		registerConfigurationPacket(ChunkedBlockRegistryStringTablePacket.TYPE, (client, buf) -> {
			ChunkedBlockRegistryStringTablePacket packet = ChunkedBlockRegistryStringTablePacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunked string table packet from server [UUID = {}]...", packet.uuid());
			
			ChunkedRegistryHandler.acceptPropertyStringTable(packet.uuid(), packet.tableOffset(), packet.tableChunk());
			
			return PacketByteBufs.empty();
		});

		// Decode property instance table
		registerConfigurationPacket(ChunkedBlockRegistryPropertyPacket.TYPE, (client, buf) -> {
			ChunkedBlockRegistryPropertyPacket packet = ChunkedBlockRegistryPropertyPacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunked property table packet from server [UUID = {}]...", packet.uuid());
			
			ChunkedRegistryHandler.acceptProperties(packet.uuid(), packet.propertyOffset(), packet.propertyRepresentatives());
			
			return PacketByteBufs.empty();
		});

		registerConfigurationPacket(ChunkedBlockRegistryBlockInfoPacket.TYPE, (client, buf) -> {
			ChunkedBlockRegistryBlockInfoPacket packet = ChunkedBlockRegistryBlockInfoPacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunked block info packet from server [UUID = {}]...", packet.uuid());
			
			ChunkedRegistryHandler.acceptBlockInfo(packet.uuid(), packet.blockInfoOffset(), packet.blockInfoArray());
			
			return PacketByteBufs.empty();
		});

		registerConfigurationPacket(ChunkedBlockRegistryCompletePacket.TYPE, (client, buf) -> {
			ChunkedBlockRegistryCompletePacket packet = ChunkedBlockRegistryCompletePacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunking complete packet from server [UUID = {}]...", packet.uuid());
			
			BlockInfoRegistry registry = ChunkedRegistryHandler.build(packet.uuid());
			
			ClientAckResponse response = RegistryRemapHandler.handleRegistryReceived(registry);
			
			FriendlyByteBuf responseBuf = PacketByteBufs.create();
			responseBuf.writeUtf(response.getSerializedName());
			return responseBuf;
		});
		
	}
	
	
	public static void registerPackets() {
		// Registers network info and "you're logging into a velocity server" messages
		registerLoginPackets();

		// Register packets we need because of velocity
		registerPlayPackets();
		
		// Register packets that can be reconfigured on a velocity server
		registerConfigurationPackets();
	}

}
