package com.joshiegemfinder.synchronisedblockstates.common.network.velocity.packet;

import java.nio.charset.StandardCharsets;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C packet.
 * The velocity plugin will wrap any LOGIN phase custom queries from the server in this packet, and send it in the PLAY phase.
 */
public record VelocityCustomQueryPacket(ResourceLocation channelId, int transactionId, byte[] data) {
	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "velocity_login_packet");

	public static void encode(FriendlyByteBuf buf, VelocityCustomQueryPacket packet) {
		// == writeResourceLocation() alternative that Velocity can also use ==
		byte[] channelIdBytes = packet.channelId().toString().getBytes(StandardCharsets.UTF_8);
		buf.writeInt(channelIdBytes.length);
		buf.writeBytes(channelIdBytes);
		// ====================================================================
		
		buf.writeInt(packet.transactionId());
		byte[] data = packet.data();
		int dataSize = data.length;
		buf.writeInt(dataSize);
		buf.writeBytes(data);
	}

	public static VelocityCustomQueryPacket decode(FriendlyByteBuf buf) {
		// == readResourceLocation() alternative that Velocity can also use ==
		int channelIdLength = buf.readInt();
		byte[] channelIdBytes = new byte[channelIdLength];
		buf.readBytes(channelIdBytes);
		String channelIdString = new String(channelIdBytes, StandardCharsets.UTF_8);
		ResourceLocation channelId = new ResourceLocation(channelIdString);
		// ===================================================================
		
		int transactionId = buf.readInt();
		int dataSize = buf.readInt();
		byte[] data = new byte[dataSize];
		buf.readBytes(data);
		return new VelocityCustomQueryPacket(channelId, transactionId, data);
	}
}
