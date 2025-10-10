package com.joshiegemfinder.synchronisedblockstates.common.network.util;

import net.minecraft.util.StringRepresentable;

public enum ClientAckResponse implements IClientAckResponse, StringRepresentable {
	OK("ok", true, false, false),
	
	CLIENT_MISSING_BLOCKSTATES("clientFail", true, true, false),
	SERVER_MISSING_BLOCKSTATES("serverFail", true, false, true),
	BOTH_MISSING_BLOCKSTATES("bothFail", true, true, true),
	
	CLIENT_FATALLY_MISSING_BLOCKSTATES("clientFatal", false, true, false),
	SERVER_FATALLY_MISSING_BLOCKSTATES("serverFatal", false, false, true),
	BOTH_FATALLY_MISSING_BLOCKSTATES("bothFatal", false, true, true);
	

	private final String id;
	private final boolean success;
	private final boolean clientFail;
	private final boolean serverFail;
	
	
	private ClientAckResponse(String id, boolean success, boolean clientFail, boolean serverFail) {
		this.id = id;
		this.success = success;
		
		this.clientFail = clientFail;
		this.serverFail = serverFail;
	}

	@Override
	public boolean isSuccess() {
		return this.success;
	}

	@Override
	public boolean isClientAtFault() {
		return this.clientFail;
	}

	@Override
	public boolean isServerAtFault() {
		return this.serverFail;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}
}