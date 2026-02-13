package com.joshiegemfinder.synchronisedblockstates.common.network.velocity.packet;

import javax.annotation.Nullable;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.LoginTaskProbePacket;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet that tracks info about the Velocity proxy.
 * 
 * Three types of configuration info:
 * 1. Configuration begins
 * 2. Configuration is still happening
 * 3. Configuration ends
 * 
 * The server network version is sent to the client when it becomes available, under CONTINUING_CONFIGURATION
 */
public record VelocityNetworkInfoPacket(ConfigurationStatus configStatus, @Nullable LoginTaskProbePacket serverNetworkVersion) {
	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "velocity_network_info");
	
	public static enum ConfigurationStatus {
		// Reset everything
		BEGINNING_CONFIGURATION,
		// Configuration hasn't started or stopped since the last info, don't reset mappings
		CONTINUING_CONFIGURATION,
		// If no states have been received, go to vanilla mappings
		ENDING_CONFIGURATION;
	}
	
	public static void encode(FriendlyByteBuf buf, VelocityNetworkInfoPacket packet) {
		buf.writeInt(packet.configStatus().ordinal());
		LoginTaskProbePacket serverNetworkVersion = packet.serverNetworkVersion();
		buf.writeBoolean(serverNetworkVersion != null);
		if(serverNetworkVersion != null) {
			LoginTaskProbePacket.encode(buf, serverNetworkVersion);
		}
	}
	
	public static VelocityNetworkInfoPacket decode(FriendlyByteBuf buf) {
		int configurationStatusOrdinal = buf.readInt();
		ConfigurationStatus configurationStatus = ConfigurationStatus.values()[configurationStatusOrdinal];
		boolean hasServerNetworkVersion = buf.readBoolean();
		LoginTaskProbePacket serverNetworkVersion;
		if(hasServerNetworkVersion) {
			serverNetworkVersion = LoginTaskProbePacket.decode(buf);
		} else {
			serverNetworkVersion = null;
		}
		return new VelocityNetworkInfoPacket(configurationStatus, serverNetworkVersion);
	}
}
