package com.joshiegemfinder.synchronisedblockstates.common.client.handler;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.client.util.MappingUtil;

public class ClientNetworkHandler {

	// Behind a Velocity proxy, a 1.20.1 client will not be in the login phase when it should receive custom packets
	// (This problem goes away in 1.20.2+ with the configuration phase)
	// Because we can't send login phase packets to a 1.20.1 client, we use a Velocity plugin that:
	// 1. Notifies the client that it's connecting to a Velocity proxy when it first connects (this is the only LOGIN packet we can send)
	//    * This tells the client not to default to vanilla blockstates after the LOGIN phase
	// 2. Sends a PLAY phase packet whenever it reconfigures a client that notify the client that reconfiguring has begun
	// 3. Converts anything that would be a server->client LOGIN phase packet to the PLAY phase, in a custom wrapper packet
	// 4. Waits for the client's response and converts it to a client->server LOGIN packet response to give to the server
	// 5. Sends a PLAY phase packet that notifies the client that configuration has ended
	
	private static boolean inVelocityMode = false;
	private static boolean isVelocityReconfiguring = false;
	private static boolean hasReceivedRegistryDuringConfiguration = false;
	
	// ======== VELOCITY MODE SETTER/GETTER START ========
	public static void setVelocityMode(boolean velocityMode) {
		inVelocityMode = velocityMode;
		if(!velocityMode) {
			isVelocityReconfiguring = false;
		}
	}
	
	public static boolean getVelocityMode() {
		return inVelocityMode;
	}
	// ========= VELOCITY MODE SETTER/GETTER END =========

	// ======== SHARED CONFIGURATION CODE START ========
	public static void onConfigurationBegin(boolean isMemoryConnection) {
		// Clear decoders
		ChunkedRegistryHandler.clearDecoders();
		
		// If it's singleplayer, reset to original registry
		if(isMemoryConnection) {
			MappingUtil.restoreToOriginalBlockStateRegistry();
		}
		
		hasReceivedRegistryDuringConfiguration = false;
	}

	public static void onConfigurationEnd(boolean isMemoryConnection) {
		// If we didn't receive a registry during this reconfiguration (and it's not singleplayer), revert to vanilla states
		if(!hasReceivedRegistryDuringConfiguration && !isMemoryConnection) {
			RegistryRemapHandler.revertToVanillaBlockstates();
		}
		
		hasReceivedRegistryDuringConfiguration = false;
	}

	public static void markRegistryRecieved() {
		hasReceivedRegistryDuringConfiguration = true;
	}
	
	public static void setServerNetworkVersion(int preferredNetworkVersion, int[] fallbackNetworkVersions) {
		if(preferredNetworkVersion != SynchronisedBlockstates.NETWORK_VERSION) {
			SynchronisedBlockstates.LOGGER.warn("Connecting to a server running network version {} on a client running network version {}", preferredNetworkVersion, SynchronisedBlockstates.NETWORK_VERSION);
		}
	}
	// ======== SHARED CONFIGURATION CODE END ========
	
	public static void onLoginPhaseBegan(boolean isMemoryConnection) {
		// Disable velocity mode, because we've only just began the login phase
		// We receive the velocity connection packet during the login phase
		setVelocityMode(false);
		
		// We can't know if we're connecting to velocity yet, so assume the LOGIN phase is fair play
		onConfigurationBegin(isMemoryConnection);
	}
	
	public static void onLoginPhaseEnd(boolean isMemoryConnection) {
		// If we're not connected to a velocity server, the end of LOGIN is the end of configuration
		if(!inVelocityMode) {
			onConfigurationEnd(isMemoryConnection);
		}
	}
	
	public static void onVelocityConfigurationStart() {
		// Only run code if we're in velocity mode
		if(!inVelocityMode) {
			return;
		}

		isVelocityReconfiguring = true;
		
		onConfigurationBegin(false);
	}
	
	public static void onVelocityConfigurationEnd() {
		// Only run code if we're in velocity mode and in a valid configuration frame
		if(!inVelocityMode || !isVelocityReconfiguring) {
			return;
		}

		onConfigurationEnd(false);
		
		isVelocityReconfiguring = false;
	}

	// On disconnect, reset everything to prevent bleed between loads
	public static void onDisconnectFromServer() {
		inVelocityMode = false;
		isVelocityReconfiguring = false;
		hasReceivedRegistryDuringConfiguration = false;
		MappingUtil.restoreToOriginalBlockStateRegistry();
	}
}
