package com.joshiegemfinder.synchronisedblockstates.common.network.packet;

import java.util.UUID;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ChunkedBlockRegistryStartPacket(UUID uuid, int totalPropertyCount, int totalBlockCount) {

	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "chunked_block_sync_start");

	public static void encode(FriendlyByteBuf buf, ChunkedBlockRegistryStartPacket packet) {
		buf.writeUUID(packet.uuid());
		buf.writeInt(packet.totalPropertyCount());
		buf.writeInt(packet.totalBlockCount());
	}
	
	public static ChunkedBlockRegistryStartPacket decode(FriendlyByteBuf buf) {
		UUID uuid = buf.readUUID();
		int totalPropertyCount = buf.readInt();
		int totalBlockCount = buf.readInt();
		return new ChunkedBlockRegistryStartPacket(uuid, totalPropertyCount, totalBlockCount);
	}
	
}
