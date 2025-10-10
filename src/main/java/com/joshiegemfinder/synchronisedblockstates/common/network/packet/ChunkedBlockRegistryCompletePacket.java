package com.joshiegemfinder.synchronisedblockstates.common.network.packet;

import java.util.UUID;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ChunkedBlockRegistryCompletePacket(UUID uuid) {

	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "chunked_block_sync_complete");

	public static void encode(FriendlyByteBuf buf, ChunkedBlockRegistryCompletePacket packet) {
		buf.writeUUID(packet.uuid());
	}
	
	public static ChunkedBlockRegistryCompletePacket decode(FriendlyByteBuf buf) {
		UUID uuid = buf.readUUID();
		return new ChunkedBlockRegistryCompletePacket(uuid);
	}
	
}
