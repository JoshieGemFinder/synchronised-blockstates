package com.joshiegemfinder.synchronisedblockstates.common.network.packet;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.network.SynchronisedBlockstatesNetworkVersions;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record LoginTaskProbePacket(int networkVersion, int[] fallbackNetworkVersions) {
	public static final ResourceLocation TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "sync_task_probe");
	// Alternate version of this event where the server network version(s) aren't sent, and the client responds with just their network version
	public static final ResourceLocation VELOCITY_TYPE = new ResourceLocation(SynchronisedBlockstates.MOD_ID, "velocity_network_version_probe");

	public LoginTaskProbePacket() {
		this(SynchronisedBlockstates.NETWORK_VERSION, SynchronisedBlockstates.SUPPORTED_FALLBACK_NETWORK_VERSIONS);
	}
	
	// Only supported for network versions >= 5 (SynchronisedBlockstatesNetworkVersions.FALLBACK_VERSIONS_INTRODUCED)
	public static void encodeFallbackVersions(FriendlyByteBuf buf, LoginTaskProbePacket packet) {
		final int[] fallbackNetworkVersions = packet.fallbackNetworkVersions();
		if(fallbackNetworkVersions == null) {
			buf.writeInt(0);
		} else {
			final int fallbackVersionCount = fallbackNetworkVersions.length;
			buf.writeInt(fallbackVersionCount);
			for(int i = 0; i < fallbackVersionCount; ++i) {
				buf.writeInt(fallbackNetworkVersions[i]);
			}
		}
	}
	
	public static void encode(FriendlyByteBuf buf, LoginTaskProbePacket packet) {
		final int networkVersion = packet.networkVersion();
		buf.writeInt(networkVersion);
		// If network version >= 5, also encode any fallback network versions that are supported
		if(networkVersion >= SynchronisedBlockstatesNetworkVersions.FALLBACK_VERSIONS_INTRODUCED) {
			encodeFallbackVersions(buf, packet);
		}
	}
	
	public static int[] decodeFallbackVersions(FriendlyByteBuf buf) {
		final int fallbackVersionCount = buf.readInt();
		int[] fallbackVersions = new int[fallbackVersionCount];
		for(int i = 0; i < fallbackVersionCount; ++i) {
			fallbackVersions[i] = buf.readInt();
		}
		return fallbackVersions;
	}
	
	public static LoginTaskProbePacket decode(FriendlyByteBuf buf) {
		int version = buf.readInt();
		final int[] fallbackVersions;
		if(version >= SynchronisedBlockstatesNetworkVersions.FALLBACK_VERSIONS_INTRODUCED) {
			fallbackVersions = decodeFallbackVersions(buf);
		} else {
			fallbackVersions = null;
		}
		return new LoginTaskProbePacket(version, fallbackVersions);
	}
}
