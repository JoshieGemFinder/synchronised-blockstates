package com.joshiegemfinder.synchronisedblockstates.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public final class ServerBlockInfoWrapper extends BlockInfoWrapper {

	protected final Block block;
//	protected final BlockRepresentative blockRepresentative;
	protected final int propertyCount;
	protected final Property<?>[] properties;
	protected final Object[][] propertyValues;
	protected final PropertyRepresentative[] propertyRepresentatives;
	protected final int stateCount;
	protected final int[] stateGlobalIndexes;
	
	
	public ServerBlockInfoWrapper(ResourceKey<Block> key, Block block) {
		super(key);
		this.block = block;

		// order properties
		final Property<?>[] properties = this.properties = orderProperties(block);
		
		final int propertyCount = this.propertyCount = properties.length;
		
		final PropertyRepresentative[] propertyRepresentatives = this.propertyRepresentatives = new PropertyRepresentative[propertyCount];
		final Object[][] propertyValues = this.propertyValues = new Object[propertyCount][];
		
		int stateCount = 1;
		
		for(int i = 0; i < propertyCount; ++i) {
			// Get property
			final Property<?> property = properties[i];
			// Get value count
			final int valueCount = property.getPossibleValues().size();
			// Get property representative
			PropertyRepresentative propertyRepresentative = propertyRepresentatives[i] = PropertyRepresentative.of(property);
			
			// Convert sorted strings to usable values for speed when we accept blockstates later on
			Object[] values = propertyValues[i] = new Object[valueCount];
			final String[] internedAllowedValues = propertyRepresentative.allowedValues();
			for(int j = 0; j < valueCount; ++j) {
				// TODO this might be bad for performance, switch to relying on InternKey::sortValues
				values[j] = property.getValue(internedAllowedValues[j]).get();
			}
			
			// Keep track of the number of states
			stateCount *= valueCount;
		}
		
		this.stateCount = stateCount;
		this.stateGlobalIndexes = new int[stateCount];
	}

	protected Property<?>[] orderProperties(Block block) {
		StateDefinition<Block, BlockState> stateDefinition = block.getStateDefinition();
		List<Property<?>> propertyList = new ArrayList<>(stateDefinition.getProperties());
		propertyList.sort(PropertyRepresentative::compare);
		return propertyList.toArray(new Property<?>[0]);
	}
	
	public Block getBlock() {
		return this.block;
	}
	
	public Property<?>[] getPropertyObjects() {
		return this.properties;
	}

	protected int getBlockStateIndex(BlockState state) {
		int index = 0;
		for(int propertyIndex = 0; propertyIndex < this.properties.length; ++propertyIndex) {
			Property<?> property = this.properties[propertyIndex];
			
			Optional<?> valueOptional = state.getOptionalValue(property);
			if(valueOptional.isEmpty()) {
				return -1;
			}
			
			Object stateValue = valueOptional.get();
			
			Object[] values = this.propertyValues[propertyIndex];

			index *= values.length;
			
			for(int i = 0; i < values.length; ++i) {
				Object value = values[i];

				if(Objects.equals(value, stateValue)) {
//				if(value == stateValue) {
					index += i;
					break;
				}
			}
		}
		
		return index;
	}

	public void acceptBlockState(BlockState state, int globalIndex) {
		int stateIndex = getBlockStateIndex(state);
		this.stateGlobalIndexes[stateIndex] = globalIndex;
	}
	
	@Override
	public PropertyRepresentative[] getProperties() {
		return this.propertyRepresentatives;
	}

	@Override
	public int[] getStateGlobalIndexes() {
		return this.stateGlobalIndexes;
	}
	
	@Override
	public int getStateCount() {
		return this.stateCount;
	}
}
