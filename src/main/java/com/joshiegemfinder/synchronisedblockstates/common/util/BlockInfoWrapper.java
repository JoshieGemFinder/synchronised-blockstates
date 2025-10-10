package com.joshiegemfinder.synchronisedblockstates.common.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

public abstract sealed class BlockInfoWrapper permits ServerBlockInfoWrapper, ClientBlockInfoWrapper, RegistryBlockInfoWrapper {

	protected final ResourceKey<Block> key;
	
	public BlockInfoWrapper(ResourceKey<Block> key) {
		this.key = key;
	}
	
	public final ResourceKey<Block> getKey() {
		return this.key;
	}
	
	public abstract PropertyRepresentative[] getProperties();
	public int getPropertyCount() {
		return this.getProperties().length;
	}
	
	public abstract int[] getStateGlobalIndexes();
	public int getStateCount() {
		return this.getStateGlobalIndexes().length;
	}
	
	public static void encode(FriendlyByteBuf buf, final BlockInfoWrapper blockInfo) {
		// Write block key
		buf.writeResourceKey(blockInfo.getKey());

		// Write properties
		final PropertyRepresentative[] properties = blockInfo.getProperties();
		final int propertyCount = properties.length;
		
		// Write property count
		buf.writeVarInt(propertyCount);
		// Write each individual property
		for(int i = 0; i < propertyCount; ++i) {
			PropertyRepresentative.encode(buf, properties[i]);
		}
		
		// Write states
		final int[] states = blockInfo.getStateGlobalIndexes();
		final int stateCount = states.length;
		
		// "State count can be calculated from properties" - update: it cannot, because air
//		buf.writeVarIntArray(states);
		// Write state count
		buf.writeVarInt(stateCount);
		// Write each individual state index
		for(int i = 0; i < stateCount; ++i) {
			buf.writeVarInt(states[i]);
		}
	}
	
	public static BlockInfoWrapper decode(FriendlyByteBuf buf) {
		// Read the block key
		ResourceKey<Block> blockKey = buf.readResourceKey(Registries.BLOCK);

//		// State count can be calculated from properties
		int stateCount = 1;
		
		// Read property count
		final int propertyCount = buf.readVarInt();
		
		// Read properties
		final PropertyRepresentative[] properties = new PropertyRepresentative[propertyCount];

		for(int i = 0; i < propertyCount; ++i) {
//			properties[i] = PropertyRepresentative.decode(buf);
			PropertyRepresentative property = properties[i] = PropertyRepresentative.decode(buf);
			stateCount *= property.allowedValues().length;
		}
		
		// Read global state indexes
//		final int stateCount = buf.readVarInt();
		int[] stateGlobalIndexes = new int[stateCount];
		for(int i = 0; i < stateCount; ++i) {
			stateGlobalIndexes[i] = buf.readVarInt();
		}
//		int[] stateGlobalIndexes = buf.readVarIntArray();
		
		return new ClientBlockInfoWrapper(blockKey, properties, stateGlobalIndexes);
	}
}
