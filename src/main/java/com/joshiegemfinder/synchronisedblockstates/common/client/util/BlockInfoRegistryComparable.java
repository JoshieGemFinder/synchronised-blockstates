package com.joshiegemfinder.synchronisedblockstates.common.client.util;

import java.util.ArrayList;
import java.util.List;

import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry;
import com.joshiegemfinder.synchronisedblockstates.common.util.RegistryBlockInfoWrapper;

import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

public class BlockInfoRegistryComparable {

	private final List<ResourceKey<Block>> orderedKeys;
	private final Reference2ObjectOpenHashMap<ResourceKey<Block>, RegistryBlockInfoWrapper.Impl> blockInfoMap;

	public BlockInfoRegistryComparable(BlockInfoRegistry registry) {
		final RegistryBlockInfoWrapper.Impl[] blocks = registry.getBlocks();
		orderedKeys = new ArrayList<ResourceKey<Block>>(blocks.length);
		blockInfoMap = new Reference2ObjectOpenHashMap<ResourceKey<Block>, RegistryBlockInfoWrapper.Impl>(blocks.length);
		
		for(int i = 0; i < blocks.length; ++i) {
			final RegistryBlockInfoWrapper.Impl block = blocks[i];
			final ResourceKey<Block> key = block.getKey();
			orderedKeys.add(key);
			blockInfoMap.put(key, block);
		}
	}

	public BlockInfoRegistryComparable(List<ResourceKey<Block>> defaultKeyOrdering, BlockInfoRegistry registry) {
		final RegistryBlockInfoWrapper.Impl[] blocks = registry.getBlocks();
		final int registryBlockCount = blocks.length;
		final int defaultKeyCount = defaultKeyOrdering.size();
		
		orderedKeys = new ArrayList<ResourceKey<Block>>(registryBlockCount);
		blockInfoMap = new Reference2ObjectOpenHashMap<ResourceKey<Block>, RegistryBlockInfoWrapper.Impl>(registryBlockCount);

		Reference2IntLinkedOpenHashMap<ResourceKey<Block>> keyIndexes = new Reference2IntLinkedOpenHashMap<ResourceKey<Block>>(defaultKeyCount);
		for(int i = 0; i < defaultKeyCount; ++i) {
			keyIndexes.put(defaultKeyOrdering.get(i), i);
		}
		keyIndexes.defaultReturnValue(-1);
		
		@SuppressWarnings("unchecked")
		ResourceKey<Block>[] keysHit = new ResourceKey[defaultKeyCount];
		List<ResourceKey<Block>> keysMissed = new ArrayList<ResourceKey<Block>>(registryBlockCount);

		for(int i = 0; i < registryBlockCount; ++i) {
			final RegistryBlockInfoWrapper.Impl block = blocks[i];
			final ResourceKey<Block> key = block.getKey();
			blockInfoMap.put(key, block);
			
			int hit = keyIndexes.removeInt(key);
			if(hit != -1) {
				keysHit[hit] = key;
			} else {
				keysMissed.add(key);
			}
		}
		
		for(int i = 0; i < defaultKeyCount; ++i) {
			if(keysHit[i] != null) {
				orderedKeys.add(keysHit[i]);
			}
		}
		
		orderedKeys.addAll(keysMissed);
		
	}
}
