package com.joshiegemfinder.synchronisedblockstates.common.network.packet;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record LoginTaskProbePacket(int networkVersion) {
	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "sync_task_probe");
	// Alternate version of this event where the server network version isn't sent, and the client responds with just their network version
	public static final ResourceLocation VELOCITY_TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "velocity_network_version_probe");

	public LoginTaskProbePacket() {
		this(SynchronisedBlockstates.NETWORK_VERSION);
	}
	
	public static void encode(FriendlyByteBuf buf, LoginTaskProbePacket packet) {
		buf.writeInt(packet.networkVersion());
	}
	
	public static LoginTaskProbePacket decode(FriendlyByteBuf buf) {
		int version = buf.readInt();
		return new LoginTaskProbePacket(version);
	}
}
