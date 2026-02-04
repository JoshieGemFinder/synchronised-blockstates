package com.joshiegemfinder.synchronisedblockstates.common.network.util;

import net.minecraft.network.FriendlyByteBuf;

public record NetworkedProperty(int nameIndex, int propertyClassIndex, int[] allowedValueIndices) {
	public static void encode(FriendlyByteBuf buf, final NetworkedProperty property) {
		buf.writeVarInt(property.nameIndex);
		buf.writeVarInt(property.propertyClassIndex);
		buf.writeVarIntArray(property.allowedValueIndices);
	}

	public static NetworkedProperty decode(FriendlyByteBuf buf) {
		final int nameIndex = buf.readVarInt();
		final int propertyClassIndex = buf.readVarInt();
		final int[] allowedValueIndices = buf.readVarIntArray();
		
		return new NetworkedProperty(nameIndex, propertyClassIndex, allowedValueIndices);
	}
}
