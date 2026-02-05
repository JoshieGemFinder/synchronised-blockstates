package com.joshiegemfinder.synchronisedblockstates.fabric.network.client;

import java.util.concurrent.CompletableFuture;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.client.handler.ChunkedRegistryHandler;
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
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry;

import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

public class SynchronisedBlockstatesNetworkFabricClient {

	public static void registerPackets() {
		// Let the server know we're running synchronised blockstates
		ClientLoginNetworking.registerGlobalReceiver(LoginTaskProbePacket.TYPE, (client, handler, buf, listenerAdder) -> {
			LoginTaskProbePacket packet = LoginTaskProbePacket.decode(buf);

			SynchronisedBlockstates.LOGGER.debug("Recieved Login Probe Packet for network version {} (client network version is {})", packet.networkVersion(), SynchronisedBlockstates.NETWORK_VERSION);

			if(packet.networkVersion() != SynchronisedBlockstates.NETWORK_VERSION) {
				SynchronisedBlockstates.LOGGER.warn("Connecting to a server running network version {} on a client running network version {}", packet.networkVersion(), SynchronisedBlockstates.NETWORK_VERSION);
			}
			
			{
				FriendlyByteBuf buffer = PacketByteBufs.create();
				
				LoginTaskProbePacket response = new LoginTaskProbePacket();
				LoginTaskProbePacket.encode(buffer, response);
				
//				responseSender.sendPacket(LoginTaskProbePacket.TYPE, buffer);
				return CompletableFuture.completedFuture(buffer);
			}
		});
		

		// Handle an unchunked block registry packet
		ClientLoginNetworking.registerGlobalReceiver(UnchunkedBlockRegistryPacket.TYPE, (client, handler, buf, listenerAdder) -> {
			UnchunkedBlockRegistryPacket packet = UnchunkedBlockRegistryPacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved unchunked blockstate registry from server...");
			SynchronisedBlockstates.LOGGER.info("Blockstate registry: {}", packet.registry());
			
			ClientAckResponse response = RegistryRemapHandler.handleRegistryReceived(packet.registry());
			
			FriendlyByteBuf responseBuf = PacketByteBufs.create();
			responseBuf.writeUtf(response.getSerializedName());
			return CompletableFuture.completedFuture(responseBuf);
		});
		

		// Handle chunked block registry packets
		ClientLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryStartPacket.TYPE, (client, handler, buf, listenerAdder) -> {
			ChunkedBlockRegistryStartPacket packet = ChunkedBlockRegistryStartPacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunking start packet from server [UUID = {}]...", packet.uuid());
			
			boolean decodeYes = ChunkedRegistryHandler.startDecoding(packet.uuid(), packet.classTableSize(), packet.stringTableSize(), packet.totalPropertyCount(), packet.totalBlockCount());
			
			FriendlyByteBuf responseBuf = PacketByteBufs.create();
			responseBuf.writeBoolean(decodeYes);
			return CompletableFuture.completedFuture(responseBuf);
		});

		// Decode property class string table
		ClientLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryPropertyClassTablePacket.TYPE, (client, handler, buf, listenerAdder) -> {
			ChunkedBlockRegistryPropertyClassTablePacket packet = ChunkedBlockRegistryPropertyClassTablePacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunked class table packet from server [UUID = {}]...", packet.uuid());
			
			ChunkedRegistryHandler.acceptPropertyClasses(packet.uuid(), packet.tableOffset(), packet.tableChunk());
			
			return CompletableFuture.completedFuture(PacketByteBufs.create());
		});

		// Decode property name/value string table
		ClientLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryStringTablePacket.TYPE, (client, handler, buf, listenerAdder) -> {
			ChunkedBlockRegistryStringTablePacket packet = ChunkedBlockRegistryStringTablePacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunked string table packet from server [UUID = {}]...", packet.uuid());
			
			ChunkedRegistryHandler.acceptPropertyStringTable(packet.uuid(), packet.tableOffset(), packet.tableChunk());
			
			return CompletableFuture.completedFuture(PacketByteBufs.create());
		});

		// Decode property instance table
		ClientLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryPropertyPacket.TYPE, (client, handler, buf, listenerAdder) -> {
			ChunkedBlockRegistryPropertyPacket packet = ChunkedBlockRegistryPropertyPacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunked property table packet from server [UUID = {}]...", packet.uuid());
			
			ChunkedRegistryHandler.acceptProperties(packet.uuid(), packet.propertyOffset(), packet.propertyRepresentatives());
			
			return CompletableFuture.completedFuture(PacketByteBufs.create());
		});

		ClientLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryBlockInfoPacket.TYPE, (client, handler, buf, listenerAdder) -> {
			ChunkedBlockRegistryBlockInfoPacket packet = ChunkedBlockRegistryBlockInfoPacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunked block info packet from server [UUID = {}]...", packet.uuid());
			
			ChunkedRegistryHandler.acceptBlockInfo(packet.uuid(), packet.blockInfoOffset(), packet.blockInfoArray());
			
			return CompletableFuture.completedFuture(PacketByteBufs.create());
		});

		ClientLoginNetworking.registerGlobalReceiver(ChunkedBlockRegistryCompletePacket.TYPE, (client, handler, buf, listenerAdder) -> {
			ChunkedBlockRegistryCompletePacket packet = ChunkedBlockRegistryCompletePacket.decode(buf);

			SynchronisedBlockstates.LOGGER.info("Recieved chunking complete packet from server [UUID = {}]...", packet.uuid());
			
			BlockInfoRegistry registry = ChunkedRegistryHandler.build(packet.uuid());
			
			ClientAckResponse response = RegistryRemapHandler.handleRegistryReceived(registry);
			
			FriendlyByteBuf responseBuf = PacketByteBufs.create();
			responseBuf.writeUtf(response.getSerializedName());
			return CompletableFuture.completedFuture(responseBuf);
		});
	}

}
