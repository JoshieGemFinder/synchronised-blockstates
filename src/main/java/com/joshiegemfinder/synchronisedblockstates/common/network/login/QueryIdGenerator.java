package com.joshiegemfinder.synchronisedblockstates.common.network.login;

import java.util.concurrent.atomic.AtomicInteger;

public interface QueryIdGenerator {
	public static QueryIdGenerator create(int initial) {
		return new QueryIdGenerator() {
			private final AtomicInteger currentQueryId = new AtomicInteger(initial);

			@Override
			public int nextQueryId() {
				return this.currentQueryId.getAndIncrement();
			}
		};
	}
	
	public static QueryIdGenerator create() {
		return create(0);
	}

	int nextQueryId();
}
