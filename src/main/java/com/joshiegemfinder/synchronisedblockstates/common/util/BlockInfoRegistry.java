package com.joshiegemfinder.synchronisedblockstates.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/*
 * It's somewhat tempting to sort the properties in the property registry (so property A
 *     would always appear before property B) and then sort the property indexes in
 *     RegistryBlockInfoWrapper.Impl so they always appear in the same order there as well
 *     (because if A has a registry index of 0 and B has an index of 5 and 0 always appears
 *     before 5 in the block info wrapper, A will always appear before B in the block info),
 *     and then use that to simplify comparisons.
 * Ultimately comes down to how much the sorting once can simplify the set operations.
 */
public final class BlockInfoRegistry {

	protected final PropertyRepresentative[] propertyRegistry;
	protected final RegistryBlockInfoWrapper.Impl[] blockRegistry;
	
	public BlockInfoRegistry(BlockInfoWrapper[] blockInfoArray) {
		final int blockCount = blockInfoArray.length;
		
		final ObjectArrayList<PropertyRepresentative> properties = new ObjectArrayList<PropertyRepresentative>(256);
		final Reference2IntOpenHashMap<PropertyRepresentative> propertyMap = new Reference2IntOpenHashMap<PropertyRepresentative>(256);
		
		final int[][] blockPropertyIndexesStorage = new int[blockCount][];
		
		for(int blockIndex = 0; blockIndex < blockCount; ++blockIndex) {
			final BlockInfoWrapper blockInfo = blockInfoArray[blockIndex];
			
			final PropertyRepresentative[] blockProperties = blockInfo.getProperties();
			
			final int propertyCount = blockProperties.length;
			final int[] blockPropertyIndexes = new int[propertyCount];
			
			for(int i = 0; i < propertyCount; ++i) {
				final PropertyRepresentative blockProperty = blockProperties[i];
				final int blockPropertyIndex = propertyMap.computeIfAbsent(blockProperty, (PropertyRepresentative key) -> {
					final int index = properties.size();
					properties.add(key);
					return index;
				});
				blockPropertyIndexes[i] = blockPropertyIndex;
			}
			
			blockPropertyIndexesStorage[blockIndex] = blockPropertyIndexes;
		}
		
		this.propertyRegistry = properties.toArray(new PropertyRepresentative[0]);

		final RegistryBlockInfoWrapper.Impl[] blockRegistry = this.blockRegistry = new RegistryBlockInfoWrapper.Impl[blockCount];
		for(int blockIndex = 0; blockIndex < blockCount; ++blockIndex) {
			final BlockInfoWrapper blockInfo = blockInfoArray[blockIndex];
			
			blockRegistry[blockIndex] = new RegistryBlockInfoWrapper.Impl(blockInfo.getKey(), blockPropertyIndexesStorage[blockIndex], blockInfo.getStateGlobalIndexes(), this.propertyRegistry);
		}
	}

//	public int getPropertyCount() {
//		return this.propertyRegistry.length;
//	}
//
//	public PropertyRepresentative getProperty(int index) {
//		return this.propertyRegistry[index];
//	}

	public PropertyRepresentative[] getProperties() {
		return this.propertyRegistry;
	}
	
//	public int getBlockCount() {
//		return this.blockRegistry.length;
//	}
//	
//	public RegistryBlockInfoWrapper.Impl getBlock(int index) {
//		return this.blockRegistry[index];
//	}

	public RegistryBlockInfoWrapper.Impl[] getBlocks() {
		return this.blockRegistry;
	}

	public int getStateCount() {
		int stateCount = 0;
		for(RegistryBlockInfoWrapper.Impl blockInfo : this.blockRegistry) {
			stateCount += blockInfo.getStateCount();
		}
		return stateCount;
	}
	
	@Override
	public boolean equals(Object obj) {
		return (this == obj);
	}
	
	public static void encode(FriendlyByteBuf buf, final BlockInfoRegistry registry) {
		// Write properties
		final PropertyRepresentative[] properties = registry.getProperties();
		final int propertyCount = properties.length;
		
		// Write property count
		buf.writeVarInt(propertyCount);
		// Write each individual property
		for(int i = 0; i < propertyCount; ++i) {
			PropertyRepresentative.encode(buf, properties[i]);
		}
		
		// Write blocks
		final RegistryBlockInfoWrapper.Impl[] blocks = registry.getBlocks();
		final int blockCount = blocks.length;
		// Write block count
		buf.writeVarInt(blockCount);
		for(int i = 0; i < blockCount; ++i) {
			RegistryBlockInfoWrapper.encodeRegistry(buf, blocks[i]);
		}
		
	}

	public static BlockInfoRegistry decode(FriendlyByteBuf buf) {
		return new BlockInfoRegistry(buf);
	}
	
	private BlockInfoRegistry(FriendlyByteBuf buf) {
		// Read the property count
		final int propertyCount = buf.readVarInt();

		// Read the properties
		final PropertyRepresentative[] propertyRegistry = this.propertyRegistry = new PropertyRepresentative[propertyCount];
		for(int i = 0; i < propertyCount; ++i) {
			propertyRegistry[i] = PropertyRepresentative.decode(buf);
		}
		
		// Read blocks
		final int blockCount = buf.readVarInt();
		final RegistryBlockInfoWrapper.Impl[] blockRegistry = this.blockRegistry = new RegistryBlockInfoWrapper.Impl[blockCount];
		for(int i = 0; i < blockCount; ++i) {
			blockRegistry[i] = RegistryBlockInfoWrapper.decodeRegistry(buf, propertyRegistry);
		}
	}
	
	public static ChunkedRegistryDecoder startChunkDecoding(UUID uuid, int propertyCount, int blockCount) {
		return new ChunkedRegistryDecoder(uuid, propertyCount, blockCount);
	}
	
	public static final class ChunkedRegistryDecoder {
		public final UUID uuid;
		protected final PropertyRepresentative[] propertyRegistry;
		protected final RegistryBlockInfoWrapper.Impl[] blockRegistry;
		private boolean finished = false;
		
		private ChunkedRegistryDecoder(UUID uuid, int propertyCount, int blockCount) {
			this.uuid = uuid;
			this.propertyRegistry = new PropertyRepresentative[propertyCount];
			this.blockRegistry = new RegistryBlockInfoWrapper.Impl[blockCount];
		}
		
		public void acceptProperties(int propertyOffset, PropertyRepresentative[] propertyRepresentatives) {
			if(this.isFinished()) {
				throw new IllegalStateException("Tried to add properties to an already finished ChunkedRegistryDecoder");
			}
			
			System.arraycopy(propertyRepresentatives, 0, propertyRegistry, propertyOffset, propertyRepresentatives.length);
		}

		public void acceptBlocks(int blockInfoOffset, RegistryBlockInfoWrapper[] blockInfoArray) {
			if(this.isFinished()) {
				throw new IllegalStateException("Tried to add blocks to an already finished ChunkedRegistryDecoder");
			}
			
			final PropertyRepresentative[] propertyRegistry = this.propertyRegistry;
			final RegistryBlockInfoWrapper.Impl[] blockRegistry = this.blockRegistry;
			
			for(int i = 0; i < blockInfoArray.length; ++i) {
				final RegistryBlockInfoWrapper blockInfo = blockInfoArray[i];
				blockRegistry[i + blockInfoOffset] = new RegistryBlockInfoWrapper.Impl(blockInfo.key, blockInfo.propertyRepresentativeIndexes, blockInfo.stateGlobalIndexes, propertyRegistry);
			}
		}
		
		public BlockInfoRegistry build() {
			if(this.isFinished()) {
				throw new IllegalStateException("Tried to build an already finished ChunkedRegistryDecoder");
			}
			
			final PropertyRepresentative[] propertyRegistry = this.propertyRegistry;
			for(int i = 0; i < propertyRegistry.length; ++i) {
				if(propertyRegistry[i] == null) {
					throw new IllegalStateException("Trying to build a BlockInfoRegistry without receiving all properties");
				}
			}
			
			final RegistryBlockInfoWrapper.Impl[] blockRegistry = this.blockRegistry;
			for(int i = 0; i < blockRegistry.length; ++i) {
				if(blockRegistry[i] == null) {
					throw new IllegalStateException("Trying to build a BlockInfoRegistry without receiving all blocks");
				}
			}
			
			this.finished = true;
			
			return new BlockInfoRegistry(this.propertyRegistry, this.blockRegistry);
		}
		
		public boolean isFinished() {
			return this.finished;
		}
	}
	
	private BlockInfoRegistry(PropertyRepresentative[] propertyRegistry, RegistryBlockInfoWrapper.Impl[] blockRegistry) {
		this.propertyRegistry = propertyRegistry;
		this.blockRegistry = blockRegistry;
	}
	

	public static BlockInfoRegistry createRegistry(IdMapper<BlockState> mapper) {
		final int totalStateCount = mapper.size();
		
		List<ServerBlockInfoWrapper> blocksOrdered = new ArrayList<ServerBlockInfoWrapper>(BuiltInRegistries.BLOCK.size());
		Map<Block, ServerBlockInfoWrapper> blocks = new Reference2ObjectOpenHashMap<>(BuiltInRegistries.BLOCK.size());

		{
			// cache last key to speed up indexing
			Block prevKey = null;
			ServerBlockInfoWrapper prevValue = null;
			for(int i = 0; i < totalStateCount; ++i) {
				BlockState blockState = Block.stateById(i);
				Block block = blockState.getBlock();
				
				final ServerBlockInfoWrapper blockInfo;
				
				if(block == prevKey) {
					blockInfo = prevValue;
				} else {
					blockInfo = blocks.computeIfAbsent(block, (key) -> {
						@SuppressWarnings("deprecation")
						ServerBlockInfoWrapper value = new ServerBlockInfoWrapper(key.builtInRegistryHolder().key(), key);
						blocksOrdered.add(value);
						return value;
					});
				}
				
				blockInfo.acceptBlockState(blockState, i);
				
				prevKey = block;
				prevValue = blockInfo;
			}
		}
		
		return new BlockInfoRegistry(blocksOrdered.toArray(new BlockInfoWrapper[0]));
	}
}
