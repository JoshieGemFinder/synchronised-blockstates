package com.joshiegemfinder.synchronisedblockstates.common.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.joshiegemfinder.synchronisedblockstates.common.client.util.ProxyIdMapper;
import com.joshiegemfinder.synchronisedblockstates.common.mixinexports.client.BlockMixinExports;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.core.IdMapper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(value = Block.class)
public class BlockMixin {
	@SuppressWarnings("unchecked")
	@ModifyExpressionValue(method = "()V", at = @At(value = "NEW", target = "Lnet/minecraft/core/IdMapper;"))
	private static <T> IdMapper<T> proxyBlockStateRegistry(IdMapper<T> originalMapper) {
		var mapper = new ProxyIdMapper<T>(originalMapper);
		BlockMixinExports.PROXY_BLOCK_STATE_REGISTRY = (ProxyIdMapper<BlockState>)mapper;
		return mapper;
	}
}