package com.joshiegemfinder.synchronisedblockstates.common.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

public sealed abstract class RegistryBlockInfoWrapper extends BlockInfoWrapper permits RegistryBlockInfoWrapper.Impl, RegistryBlockInfoWrapper.Empty {

	protected final int[] propertyRepresentativeIndexes;
	protected final int[] stateGlobalIndexes;
	
	public RegistryBlockInfoWrapper(ResourceKey<Block> key, int[] propertyRepresentativeIndexes, int[] stateGlobalIndexes) {
		super(key);
		
		this.propertyRepresentativeIndexes = propertyRepresentativeIndexes;
		this.stateGlobalIndexes = stateGlobalIndexes;
	}
	
	public int[] getPropertyIndexes() {
		return this.propertyRepresentativeIndexes;
	}
	
	@Override
	public abstract PropertyRepresentative[] getProperties();

	@Override
	public int[] getStateGlobalIndexes() {
		return this.stateGlobalIndexes;
	}
	
	public ClientBlockInfoWrapper toClientWrapper() {
		return new ClientBlockInfoWrapper(this.getKey(), this.getProperties(), this.getStateGlobalIndexes());
	}

	public static void encodeFullArray(FriendlyByteBuf buf, final int[] stateArray) {
		final int length = stateArray.length;
		if(length < 254) {
			buf.writeByte(length);
			for(int is : stateArray) {
				buf.writeVarInt(is);
			}
		} else {
			buf.writeByte(254);
			buf.writeVarIntArray(stateArray);
		}
	}

	// Encodes the state array. Will attempt to pack it to minimise packet size without sacrificing speed
	// Not relevant just yet...
	// TODO come back once the property rework is done
	public static void encodeStateArray(FriendlyByteBuf buf, final int[] stateArray) {
		final int length = stateArray.length;
		if(length == 0) {
			buf.writeByte(0);
			return;
		}
		final int initialValue = stateArray[0];
		for(int i = 1; i < length; ++i) {
			final int value = stateArray[i];
			if(value != initialValue + i) {
				encodeFullArray(buf, stateArray);
				return;
			}
		}
		buf.writeByte(255);
		buf.writeVarInt(length); // Writing the length saves the most space
		buf.writeVarInt(initialValue);
	}
	
	public static int[] decodeStateArray(FriendlyByteBuf buf) {
		final byte len = buf.readByte();
		final int[] stateArray;
		if(len == (byte)254) {
			stateArray = buf.readVarIntArray();
		} else if(len == (byte)255) {
			final int length = buf.readVarInt();
			final int initialValue = buf.readVarInt();
			stateArray = new int[length];
			for(int i = 0; i < length; ++i) {
				stateArray[i] = initialValue + i;
			}
		} else {
			final int length = ((int)len) & 0xFF; // length in range [0, 253]
			stateArray = new int[length]; 
			for(int i = 0; i < len; ++i) {
				stateArray[i] = buf.readVarInt();
			}
		}
		return stateArray;
	}
	
	public static void encodeRegistry(FriendlyByteBuf buf, final RegistryBlockInfoWrapper blockInfo) {
		// Write block key
		buf.writeResourceKey(blockInfo.getKey());

		// Write properties
		buf.writeVarIntArray(blockInfo.getPropertyIndexes());
		
		// Write states
		buf.writeVarIntArray(blockInfo.getStateGlobalIndexes());
		// TODO come back once the property rework is done
//		encodeStateArray(buf, blockInfo.getStateGlobalIndexes());
	}

	public static final RegistryBlockInfoWrapper.Empty decodeRegistry(FriendlyByteBuf buf) {
		// Read the block key
		ResourceKey<Block> blockKey = buf.readResourceKey(Registries.BLOCK);
		
		// Read properties
		final int[] propertyRepresentativeIndexes = buf.readVarIntArray();
		
		// Read global state indexes
		final int[] stateGlobalIndexes = buf.readVarIntArray();
		// TODO come back once the property rework is done
//		final int[] stateGlobalIndexes = decodeStateArray(buf);
		
		return new RegistryBlockInfoWrapper.Empty(blockKey, propertyRepresentativeIndexes, stateGlobalIndexes);
	}

	public static final RegistryBlockInfoWrapper.Impl decodeRegistry(FriendlyByteBuf buf, PropertyRepresentative[] propertyRegistry) {
		// Read the block key
		ResourceKey<Block> blockKey = buf.readResourceKey(Registries.BLOCK);
		
		// Read properties
		final int[] propertyRepresentativeIndexes = buf.readVarIntArray();
		
		// Read global state indexes
		final int[] stateGlobalIndexes = buf.readVarIntArray();
		// TODO come back once the property rework is done
//		final int[] stateGlobalIndexes = decodeStateArray(buf);
		
		return new RegistryBlockInfoWrapper.Impl(blockKey, propertyRepresentativeIndexes, stateGlobalIndexes, propertyRegistry);
	}
	
	public static final class Empty extends RegistryBlockInfoWrapper {
		public Empty(ResourceKey<Block> key, int[] propertyRepresentativeIndexes, int[] stateGlobalIndexes) {
			super(key, propertyRepresentativeIndexes, stateGlobalIndexes);
		}
		
		@Override
		public PropertyRepresentative[] getProperties() {
			throw new UnsupportedOperationException();
		}
	}
	
	public static final class Impl extends RegistryBlockInfoWrapper {
		protected final PropertyRepresentative[] propertyRegistry;
		
		public Impl(ResourceKey<Block> key, int[] propertyRepresentativeIndexes, int[] stateGlobalIndexes, PropertyRepresentative[] propertyRegistry) {
			super(key, propertyRepresentativeIndexes, stateGlobalIndexes);
			
			this.propertyRegistry = propertyRegistry;
		}
		
		@Override
		public PropertyRepresentative[] getProperties() {
			final int[] propertyRepresentativeIndexes = this.propertyRepresentativeIndexes;
			final int propertyCount = propertyRepresentativeIndexes.length;

			final PropertyRepresentative[] propertyRegistry = this.propertyRegistry;
			
			PropertyRepresentative[] propertyRepresentatives = new PropertyRepresentative[propertyCount];
			
			for(int i = 0; i < propertyCount; ++i) {
				int propertyIndex = propertyRepresentativeIndexes[i];
				propertyRepresentatives[i] = propertyRegistry[propertyIndex];
			}
			
			return propertyRepresentatives;
		}
	}
}
