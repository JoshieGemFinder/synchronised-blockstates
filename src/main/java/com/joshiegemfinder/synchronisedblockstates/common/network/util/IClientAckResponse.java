package com.joshiegemfinder.synchronisedblockstates.common.network.util;

public interface IClientAckResponse {
	public boolean isSuccess();
	
	public default boolean isFail() {
		return !this.isSuccess();
	}
	
	public boolean isClientAtFault();
	
	public boolean isServerAtFault();
	
	public default boolean isOkay() {
		return !this.isClientAtFault() && !this.isServerAtFault();
	}

	public static boolean isSuccess(Object ack) {
		return ((IClientAckResponse)ack).isSuccess();
	}

	public static boolean isClientAtFault(Object ack) {
		return ((IClientAckResponse)ack).isClientAtFault();
	}
	
	public static boolean isServerAtFault(Object ack) {
		return ((IClientAckResponse)ack).isServerAtFault();
	}
}
