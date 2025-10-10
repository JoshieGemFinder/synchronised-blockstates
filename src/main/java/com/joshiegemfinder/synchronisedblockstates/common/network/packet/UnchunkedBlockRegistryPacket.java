package com.joshiegemfinder.synchronisedblockstates.common.network.packet;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record UnchunkedBlockRegistryPacket(BlockInfoRegistry registry) {
	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "unchunked_block_sync");

	public static void encode(FriendlyByteBuf buf, UnchunkedBlockRegistryPacket packet) {
		BlockInfoRegistry.encode(buf, packet.registry());
	}
	
	public static UnchunkedBlockRegistryPacket decode(FriendlyByteBuf buf) {
		return new UnchunkedBlockRegistryPacket(BlockInfoRegistry.decode(buf));
	}
	
}
