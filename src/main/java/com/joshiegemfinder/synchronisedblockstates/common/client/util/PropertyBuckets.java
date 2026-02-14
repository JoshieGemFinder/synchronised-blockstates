package com.joshiegemfinder.synchronisedblockstates.common.client.util;

import com.joshiegemfinder.synchronisedblockstates.common.client.util.PropertyBuckets.PropertyNameBucket.TrackingNameBucketGenerator;
import com.joshiegemfinder.synchronisedblockstates.common.network.util.NetworkedProperty;
import com.joshiegemfinder.synchronisedblockstates.common.network.util.NetworkedPropertyRegistry;
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry;
import com.joshiegemfinder.synchronisedblockstates.common.util.PropertyRepresentative;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

/**
 * Breaks up a property registry into "buckets" that can be navigated faster than a flat array.
 * 
 * Used correctly, this should allow for finding properties with matching a name and class much faster than previously.
 */
public final class PropertyBuckets {

	public static record PropertyWrapper(int propertyIndex, PropertyRepresentative runtimeProperty) {
		
	}
	
	public static record PropertyNameBucket(String propertyName, ObjectList<PropertyWrapper> properties) {
		
		public static Int2ObjectFunction<PropertyNameBucket> generateFromStringTable(String[] stringTable) {
			return (int key) -> new PropertyNameBucket(stringTable[key]);
		}
		
		public PropertyNameBucket(String propertyName) {
			this(propertyName, new ObjectArrayList<>(16));
		}
		
		public void addProperty(int propertyIndex, PropertyRepresentative runtimeProperty) {
			this.properties.add(new PropertyWrapper(propertyIndex, runtimeProperty));
		}
		
		public static class TrackingNameBucketGenerator implements Int2ObjectFunction<PropertyNameBucket> {
			private final IntSet nameIndexSet;
			private final String[] stringTable;
			
			public TrackingNameBucketGenerator(String[] stringTable) {
				this.nameIndexSet = new IntOpenHashSet(stringTable.length);
				this.stringTable = stringTable;
			}

			@Override
			public PropertyNameBucket get(int key) {
				this.nameIndexSet.add(key);
				return new PropertyNameBucket(stringTable[key]);
			}
			
			public IntSet getNameIndexSet() {
				return this.nameIndexSet;
			}
		}
		
	}
	
	public static record PropertyClassBucket(String networkPropertyClass, String runtimePropertyClass, Int2ObjectMap<PropertyNameBucket> nameBuckets) {
		
		public PropertyClassBucket(String networkPropertyClass, String runtimePropertyClass) {
			this(networkPropertyClass, runtimePropertyClass, new Int2ObjectOpenHashMap<>());
		}

		public PropertyNameBucket getNameBucket(int nameIndex) {
			return nameBuckets.get(nameIndex);
		}

		public PropertyNameBucket getOrCreateNameBucket(int nameIndex, String[] stringTable) {
			return nameBuckets.computeIfAbsent(nameIndex, PropertyNameBucket.generateFromStringTable(stringTable));
		}

		public PropertyNameBucket getOrCreateNameBucket(int nameIndex, Int2ObjectFunction<PropertyNameBucket> generator) {
			return nameBuckets.computeIfAbsent(nameIndex, generator);
		}
		
	}
	
	private final PropertyClassBucket[] classBuckets;
	// Maps RUNTIME property class --> property class index in the property class table
	// Reference2Int because all insertions and retrievals will be with interned strings
	private final Reference2IntMap<String> propertyClassToIndexMap;
	// Maps property name --> property name index in the string table
	// Reference2Int because all insertions and retrievals will be with interned strings
	private final Reference2IntMap<String> propertyNameToIndexMap;
	
	public PropertyBuckets(BlockInfoRegistry registry) {
		NetworkedPropertyRegistry networkedRegistry = registry.getNetworkedPropertyRegistry();
		
		// Maybe intern runtime class table and string table for when buckets are compared later on
		networkedRegistry.internTables();
		
		// Get property classes
		final int propertyClassTableSize = networkedRegistry.getClassTableSize();
		String[] networkPropertyClasses = networkedRegistry.getRemappedPropertyClasses();
		String[] runtimePropertyClasses = networkedRegistry.computeRuntimePropertyClasses();
		
		// Get string table
		String[] stringTable = networkedRegistry.getStringTable();
		
		// Get properties
		final int propertyTableSize = networkedRegistry.getPropertyTableSize();
		NetworkedProperty[] networkProperties = networkedRegistry.getPropertyTable();
		PropertyRepresentative[] runtimeProperties = registry.getProperties();
		
		// Create array of class name buckets
		final PropertyClassBucket[] classBuckets = this.classBuckets = new PropertyClassBucket[propertyClassTableSize];
		
		// Create reverse map for property class to class table index
		Reference2IntMap<String> propertyClassToIndexMap = this.propertyClassToIndexMap = new Reference2IntOpenHashMap<>(propertyClassTableSize);
		propertyClassToIndexMap.defaultReturnValue(-1);
		
		// Generates name buckets by indexing the string table - additionally, adds the index to propertyNameIndexSet
		final TrackingNameBucketGenerator nameBucketGenerator = new TrackingNameBucketGenerator(stringTable);
		
		for(int propertyIndex = 0; propertyIndex < propertyTableSize; ++propertyIndex) {
			NetworkedProperty networkedProperty = networkProperties[propertyIndex];
			PropertyRepresentative runtimeProperty = runtimeProperties[propertyIndex];
			
			// Get the bucket for this property class
			int propertyClassIndex = networkedProperty.propertyClassIndex();
			
			PropertyClassBucket bucket = classBuckets[propertyClassIndex];
			if(bucket == null) {
				final String runtimePropertyClassString = runtimePropertyClasses[propertyClassIndex];
				classBuckets[propertyClassIndex] = bucket = new PropertyClassBucket(networkPropertyClasses[propertyClassIndex], runtimePropertyClassString);
				// Populate the property class string --> class table index map
				propertyClassToIndexMap.put(runtimePropertyClassString, propertyClassIndex);
			}

			// Get the bucket for this property name (and property class)
			final int nameIndex = networkedProperty.nameIndex();
			
			PropertyNameBucket nameBucket = bucket.getOrCreateNameBucket(nameIndex, nameBucketGenerator);
			
			// Add this property to the (property class, property name) bucket
			nameBucket.addProperty(propertyIndex, runtimeProperty);
		}
		
		// Set of all string table indexes of name buckets
		IntSet nameIndexSet = nameBucketGenerator.getNameIndexSet();

		// Create reverse map for property name to string table index
		Reference2IntMap<String> propertyNameToIndexMap = this.propertyNameToIndexMap = new Reference2IntOpenHashMap<>(nameIndexSet.size());
		propertyNameToIndexMap.defaultReturnValue(-1);

		// Populate the property name string --> string table index map
		for(int nameIndex : nameIndexSet) {
			String propertyName = stringTable[nameIndex];
			propertyNameToIndexMap.put(propertyName, nameIndex);
		}
	}
	
	public PropertyClassBucket[] getBuckets() {
		return this.classBuckets;
	}
	
	public PropertyClassBucket getClassBucket(int propertyClassIndex) {
		return this.classBuckets[propertyClassIndex];
	}
	
	public PropertyNameBucket getClassAndNameBucket(int propertyClassIndex, int propertyNameIndex) {
		PropertyClassBucket classBucket = this.classBuckets[propertyClassIndex];
		if(classBucket == null) {
			return null;
		}
		return classBucket.getNameBucket(propertyNameIndex);
	}
	
	public ObjectList<PropertyWrapper> getProperties(int propertyClassIndex, int propertyNameIndex) {
		PropertyNameBucket nameBucket = this.getClassAndNameBucket(propertyClassIndex, propertyNameIndex);
		return nameBucket == null ? null : nameBucket.properties();
	}
	
	/**
	 * Gets the class table index of the runtime class with this name
	 * @param runtimePropertyClass the runtime name of the property class, interned via {@linkplain PropertyRepresentative#internString(String)}
	 * @return the class's index in the class table, or {@code -1} if it doesn't have a bucket
	 */
	public int getRuntimePropertyClassIndex(String runtimePropertyClass) {
		return this.propertyClassToIndexMap.getInt(runtimePropertyClass);
	}
	
	/**
	 * Gets the string table index of this property name
	 * @param propertyName the name of a property, interned via {@linkplain PropertyRepresentative#internString(String)}
	 * @return the name's index in the string table, or {@code -1} if there are no properties with this name
	 */
	public int getPropertyNameIndex(String propertyName) {
		return this.propertyNameToIndexMap.getInt(propertyName);
	}
	
}
