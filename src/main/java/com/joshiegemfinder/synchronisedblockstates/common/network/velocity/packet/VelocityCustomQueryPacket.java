package com.joshiegemfinder.synchronisedblockstates.common.network.velocity.packet;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C packet.
 * The velocity plugin will wrap any LOGIN phase custom queries from the server in this packet, and send it in the PLAY phase.
 */
public record VelocityCustomQueryPacket(ResourceLocation channelId, int transactionId, byte[] data) {
	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "velocity_login_packet");

	public static VelocityCustomQueryPacket decode(FriendlyByteBuf buf) {
		ResourceLocation channelId = buf.readResourceLocation();
		int transactionId = buf.readInt();
		int dataSize = buf.readInt();
		byte[] data = new byte[dataSize];
		buf.readBytes(data);
		return new VelocityCustomQueryPacket(channelId, transactionId, data);
	}

	public static void encode(FriendlyByteBuf buf, VelocityCustomQueryPacket packet) {
		buf.writeResourceLocation(packet.channelId());
		buf.writeInt(packet.transactionId());
		byte[] data = packet.data();
		int dataSize = data.length;
		buf.writeInt(dataSize);
		buf.writeBytes(data);
	}
}
