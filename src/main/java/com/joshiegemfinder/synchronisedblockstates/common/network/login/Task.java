package com.joshiegemfinder.synchronisedblockstates.common.network.login;

import java.util.function.Consumer;

import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;

public interface Task {
	// technically inefficient for 1.20.1, but it means the code for 1.20.1 looks about same as the code for 1.21+
	void start(Consumer<Packet<?>> consumer);

	Task.Type<?> type();

	public record Type<T extends Task>(ResourceLocation id) {
		public String toString() {
			return this.id.toString();
		}
	}
}
