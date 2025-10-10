package com.joshiegemfinder.synchronisedblockstates.common.network.packet;

import java.util.UUID;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.util.RegistryBlockInfoWrapper;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ChunkedBlockRegistryBlockInfoPacket(UUID uuid, int blockInfoOffset, RegistryBlockInfoWrapper[] blockInfoArray) {

	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "chunked_block_sync_block_info");

	public static void encode(FriendlyByteBuf buf, ChunkedBlockRegistryBlockInfoPacket packet) {
		buf.writeUUID(packet.uuid());
		buf.writeInt(packet.blockInfoOffset());
		
		final RegistryBlockInfoWrapper[] blockInfoArray = packet.blockInfoArray();
		
		buf.writeVarInt(blockInfoArray.length);
		for(int i = 0; i < blockInfoArray.length; ++i) {
			RegistryBlockInfoWrapper.encodeRegistry(buf, blockInfoArray[i]);
		}
	}
	
	public static ChunkedBlockRegistryBlockInfoPacket decode(FriendlyByteBuf buf) {
		UUID uuid = buf.readUUID();
		int blockInfoOffset = buf.readInt();
		
		final int blockInfoCount = buf.readVarInt();
		final RegistryBlockInfoWrapper[] blockInfoArray = new RegistryBlockInfoWrapper[blockInfoCount];
		for(int i = 0; i < blockInfoCount; ++i) {
			blockInfoArray[i] = RegistryBlockInfoWrapper.decodeRegistry(buf);
		}
		
		return new ChunkedBlockRegistryBlockInfoPacket(uuid, blockInfoOffset, blockInfoArray);
	}
	
}
