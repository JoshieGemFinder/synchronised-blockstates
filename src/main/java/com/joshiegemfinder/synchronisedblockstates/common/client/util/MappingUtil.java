package com.joshiegemfinder.synchronisedblockstates.common.client.util;

import java.lang.ref.SoftReference;

import org.jetbrains.annotations.ApiStatus;

import com.joshiegemfinder.synchronisedblockstates.common.mixinexports.client.BlockMixinExports;
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry;

import net.minecraft.Util;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class MappingUtil {

	public static final ProxyIdMapper<BlockState> PROXY_BLOCK_STATE_REGISTRY = Util.make(() -> {
		IdMapper<BlockState> stateRegistry = Block.BLOCK_STATE_REGISTRY;
		if(stateRegistry instanceof ProxyIdMapper<BlockState> proxyStateRegistry) {
			return proxyStateRegistry;
		} else {
			return BlockMixinExports.PROXY_BLOCK_STATE_REGISTRY;
		}
	});
	
	public static ProxyIdMapper<BlockState> getProxyMapper() {
		return PROXY_BLOCK_STATE_REGISTRY;
	}
	
	public static IdMapper<BlockState> getCurrentMapper() {
		return getProxyMapper().getSourceMapper();
	}
	
	public static void setCurrentMapper(IdMapper<BlockState> mapper) {
		getProxyMapper().setSourceMapper(mapper);
	}
	
	public static boolean isUsingCustomBlockStateRegistry() {
		return !(getProxyMapper().getSourceMapper() instanceof RemappingIdMapper<BlockState>);
	}

	public static IdMapper<BlockState> getCurrentBlockStateRegistry() {
		return getProxyMapper().getSourceMapper();
	}

	public static IdMapper<BlockState> getOriginalBlockStateRegistry() {
		IdMapper<BlockState> blockstateMapper = getCurrentMapper();
		
		while(blockstateMapper instanceof RemappingIdMapper<BlockState> proxy) {
			blockstateMapper = proxy.getOriginalMapper();
		}
		
		return blockstateMapper;
	}

	public static IdMapper<BlockState> restoreToOriginalBlockStateRegistry() {
		IdMapper<BlockState> originalMapper = getOriginalBlockStateRegistry();
		getProxyMapper().setSourceMapper(originalMapper);
		return originalMapper;
	}

	// weak reference because the polymer mod exists and *will* screw with things
	@ApiStatus.Internal
	private static SoftReference<BlockInfoRegistry> ORIGINAL_CLIENT_REGISTRY = null;
	
	public static BlockInfoRegistry getOriginalBlockInfoRegistry() {
		if(ORIGINAL_CLIENT_REGISTRY == null || ORIGINAL_CLIENT_REGISTRY.get() == null) {
			final BlockInfoRegistry registry = BlockInfoRegistry.createRegistry(getOriginalBlockStateRegistry());
			ORIGINAL_CLIENT_REGISTRY = new SoftReference<>(registry);
			return registry;
		}
		return ORIGINAL_CLIENT_REGISTRY.get();
	}
	
	public static BlockInfoRegistry getCurrentBlockInfoRegistry() {
		IdMapper<BlockState> currentMapper = getCurrentMapper();
		
		if(currentMapper == getOriginalBlockStateRegistry()) {
			return getOriginalBlockInfoRegistry();
		} else {
			return BlockInfoRegistry.createRegistry(currentMapper);
		}
	}
}
