package com.joshiegemfinder.synchronisedblockstates.common.client.util;

import java.util.Arrays;

import org.jetbrains.annotations.ApiStatus;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import net.minecraft.core.IdMapper;

public class RemappingIdMapper<T> extends IdMapper<T> {

	/**
	 * When returned from a {@link MappingProvider}, the value will not be added to the mapper
	 */
	public static final int NO_VALUE_INDEX = -1;
	
	@FunctionalInterface
	public static interface MappingProvider<T> {
		/**
		 * Remap the object with the original index to a different index</br>
		 * Returning {@link RemappedProxyIdMapper#NO_VALUE_INDEX} will hide it from the mapper, as if it had never been added
		 * @param originalIndex The original index of the value
		 * @param object The value to be added
		 * @return The new index, or {@link RemappedProxyIdMapper#NO_VALUE_INDEX} to cancel it being added to the proxy
		 */
		public int getMappedIndex(int originalIndex, T object);
	}

	@ApiStatus.Internal
	protected static class IntMappingList extends IntArrayList {
		private static final long serialVersionUID = 952144666804362924L;

		public IntMappingList(final int capacity) {
			super(capacity);
		}
		
		public IntMappingList() {
			super();
		}
		
		public void resizeForMappings(final int size) {
			if(size > this.size) {
				if(size > this.a.length) this.a = IntArrays.forceCapacity(this.a, size, this.size);
				Arrays.fill(a, this.size, size, (NO_VALUE_INDEX));
				this.size = size;
			}
		}
	}

	@ApiStatus.Internal
	protected static void putInList(IntMappingList list, int key, int value) {
		list.resizeForMappings(key + 1);
		list.set(key, value);
	}
	
	protected final IdMapper<T> originalMapper;
	protected final MappingProvider<T> mappingProvider;

	// TODO: Move to IntList!!!! Maps are WAY TOO SLOW!!!!
//	protected Int2IntMap originalIdToMappedId;
//	protected Int2IntMap mappedIdToOriginalId;
	protected IntMappingList originalIdToMappedId;
	protected IntMappingList mappedIdToOriginalId;

//	// Extra tracker to make sure nextId doesn't desync from source mapper due to outside influences
//	protected int lastNextId = this.nextId;

	public RemappingIdMapper(IdMapper<T> originalMapper, MappingProvider<T> mappingProvider) {
		this(originalMapper.size(), originalMapper, mappingProvider);
	}
	
	public RemappingIdMapper(int initialSize, IdMapper<T> originalMapper, MappingProvider<T> mappingProvider) {
		this(initialSize, originalMapper, mappingProvider, true);
	}
	
	public RemappingIdMapper(int initialSize, IdMapper<T> originalMapper, MappingProvider<T> mappingProvider, boolean initialize) {
		super(initialSize);
		this.originalMapper = originalMapper;
		this.mappingProvider = mappingProvider;

		this.originalIdToMappedId = new IntMappingList(initialSize);//new Int2IntArrayMap(initialSize);
		this.mappedIdToOriginalId = new IntMappingList(initialSize);//new Int2IntArrayMap(initialSize);
//		mappedIdToOriginalId.defaultReturnValue(NO_VALUE_INDEX);
		
		if(initialize)
			for(var entry : originalMapper.tToId.object2IntEntrySet()) {
				this.addMappedMapping(entry.getKey(), entry.getIntValue());
			}
	}

	public RemappingIdMapper(int initialSize, IdMapper<T> originalMapper, int defaultMapping, boolean initialize) {
		super(initialSize);
		this.originalMapper = originalMapper;
		this.mappingProvider = (a, b) -> defaultMapping;
		
		this.originalIdToMappedId = new IntMappingList(initialSize);
		this.mappedIdToOriginalId = new IntMappingList(initialSize);
		
		if(initialize) {
			// TODO
			Arrays.fill(originalIdToMappedId.elements(), defaultMapping);
		}
	}
	
	public IdMapper<T> getOriginalMapper() {
		return this.originalMapper;
	}
	
	protected int mapped(int originalIndex, T object) {
		int mappedIndex = this.mappingProvider.getMappedIndex(originalIndex, object);
		this.setMapping(originalIndex, mappedIndex);
		return mappedIndex;
	}
	
	public int setMapping(int originalIndex, int mappedIndex) {
		putInList(originalIdToMappedId, originalIndex, mappedIndex);
		
		if(mappedIndex != NO_VALUE_INDEX)
			putInList(mappedIdToOriginalId, mappedIndex, originalIndex);
		return mappedIndex;
	}

//	private void trackNextIdChanges() {
//		if(this.lastNextId != this.nextId) { // nextId has been unexpectedly changed
//			this.lastNextId = getOriginalMapper().nextId = this.nextId;
//		} else { // otherwise, update to match any unexpected nextId changes to the original mapper
//			this.lastNextId = this.nextId = getOriginalMapper().nextId;
//		}
//	}

	@Override
	public void addMapping(final T object, final int i) {
		this.getOriginalMapper().addMapping(object, i);
		this.addMappedMapping(object, i);
//		this.trackNextIdChanges();
	}

	public void addMappedMapping(final T object, final int i) {
		final int mappedIndex = mapped(i, object);
		if(mappedIndex != NO_VALUE_INDEX)
			this.addMappingRaw(object, mappedIndex);
	}
	
	public void addMappingRaw(final T object, final int i) {
		super.addMapping(object, i);
	}

	@Override
	public void add(T object) {
//		this.trackNextIdChanges();
		// We add mappings based off of the original mapper's ids
		this.addMapping(object, getOriginalMapper().nextId);
		// Alternatively:
//		getOriginalMapper().add(object);
//		final int mappedIndex = mapped(getOriginalMapper().getId(object), object);
//		if(mappedIndex != NO_VALUE_INDEX)
//			this.addMappingRaw(object, mappedIndex);
	}

	public void addRaw(T object) {
		super.add(object);
	}
	
}
