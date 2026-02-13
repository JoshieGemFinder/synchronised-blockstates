package com.joshiegemfinder.synchronisedblockstates.common.network.velocity.packet;

import javax.annotation.Nullable;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S packet.
 * The velocity plugin will unwrap this packet into LOGIN phase responses for custom queries that the server sent.
 */
public record VelocityCustomQueryResponsePacket(int transactionId, @Nullable byte[] data) {
	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "velocity_login_packet_response");

	public static byte[] toBytes(ByteBuf buf) {
		byte[] bytes = new byte[buf.readableBytes()];
		buf.readBytes(bytes);
		return bytes;
	}
	
	public VelocityCustomQueryResponsePacket(int transactionId, @Nullable ByteBuf data) {
		this(transactionId, data == null ? null : toBytes(data));
	}
	
	public static VelocityCustomQueryResponsePacket decode(FriendlyByteBuf buf) {
		int transactionId = buf.readInt();
		
		boolean understood = buf.readBoolean();
		
		byte[] data;
		if(understood) {
			int dataSize = buf.readInt();
			data = new byte[dataSize];
			buf.readBytes(data);
		} else {
			data = null;
		}
		
		return new VelocityCustomQueryResponsePacket(transactionId, data);
	}

	public static void encode(FriendlyByteBuf buf, VelocityCustomQueryResponsePacket packet) {
		buf.writeInt(packet.transactionId());
		byte[] data = packet.data();
		boolean understood = data != null;
		
		buf.writeBoolean(understood);
		
		if(understood) {
			int dataSize = data.length;
			buf.writeInt(dataSize);
			buf.writeBytes(data);
		}
	}
}
