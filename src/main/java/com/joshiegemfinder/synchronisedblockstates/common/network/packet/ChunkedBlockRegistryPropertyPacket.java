package com.joshiegemfinder.synchronisedblockstates.common.network.packet;

import java.util.UUID;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.util.PropertyRepresentative;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ChunkedBlockRegistryPropertyPacket(UUID uuid, int propertyOffset, PropertyRepresentative[] propertyRepresentatives) {

	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "chunked_block_sync_properties");

	public static void encode(FriendlyByteBuf buf, ChunkedBlockRegistryPropertyPacket packet) {
		buf.writeUUID(packet.uuid());
		buf.writeInt(packet.propertyOffset());
		
		final PropertyRepresentative[] propertyRepresentatives = packet.propertyRepresentatives();
		
		buf.writeVarInt(propertyRepresentatives.length);
		for(int i = 0; i < propertyRepresentatives.length; ++i) {
			PropertyRepresentative.encode(buf, propertyRepresentatives[i]);
		}
	}
	
	public static ChunkedBlockRegistryPropertyPacket decode(FriendlyByteBuf buf) {
		UUID uuid = buf.readUUID();
		int propertyOffset = buf.readInt();
		
		final int propertyCount = buf.readVarInt();
		final PropertyRepresentative[] propertyRepresentatives = new PropertyRepresentative[propertyCount];
		for(int i = 0; i < propertyCount; ++i) {
			propertyRepresentatives[i] = PropertyRepresentative.decode(buf);
		}
		
		return new ChunkedBlockRegistryPropertyPacket(uuid, propertyOffset, propertyRepresentatives);
	}
	
}
