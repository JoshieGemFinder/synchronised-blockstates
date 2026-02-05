package com.joshiegemfinder.synchronisedblockstates.common.network.packet;

import java.util.UUID;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ChunkedBlockRegistryPropertyClassTablePacket(UUID uuid, int tableOffset, String[] tableChunk) {

	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "chunked_block_sync_property_class_table");

	public static void encode(FriendlyByteBuf buf, ChunkedBlockRegistryPropertyClassTablePacket packet) {
		buf.writeUUID(packet.uuid());
		buf.writeInt(packet.tableOffset());
		
		final String[] tableChunk = packet.tableChunk();
		
		buf.writeVarInt(tableChunk.length);
		for(int i = 0; i < tableChunk.length; ++i) {
			buf.writeUtf(tableChunk[i]);
		}
	}
	
	public static ChunkedBlockRegistryPropertyClassTablePacket decode(FriendlyByteBuf buf) {
		UUID uuid = buf.readUUID();
		int tableOffset = buf.readInt();
		
		final int chunkSize = buf.readVarInt();
		final String[] tableChunk = new String[chunkSize];
		for(int i = 0; i < chunkSize; ++i) {
			tableChunk[i] = buf.readUtf();
		}
		
		return new ChunkedBlockRegistryPropertyClassTablePacket(uuid, tableOffset, tableChunk);
	}
	
}
