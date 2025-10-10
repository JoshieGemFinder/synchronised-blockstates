package com.joshiegemfinder.synchronisedblockstates.common.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

public final class ClientBlockInfoWrapper extends BlockInfoWrapper {

	protected final PropertyRepresentative[] propertyRepresentatives;
	protected final int[] stateGlobalIndexes;
	
	public ClientBlockInfoWrapper(ResourceKey<Block> key, PropertyRepresentative[] propertyRepresentatives, int[] stateGlobalIndexes) {
		super(key);

		this.propertyRepresentatives = propertyRepresentatives;

		this.stateGlobalIndexes = stateGlobalIndexes;
	}

	@Override
	public PropertyRepresentative[] getProperties() {
		return this.propertyRepresentatives;
	}

	@Override
	public int[] getStateGlobalIndexes() {
		return this.stateGlobalIndexes;
	}
}
