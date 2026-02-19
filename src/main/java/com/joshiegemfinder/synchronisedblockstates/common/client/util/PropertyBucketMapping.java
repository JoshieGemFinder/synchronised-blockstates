package com.joshiegemfinder.synchronisedblockstates.common.client.util;

import javax.annotation.Nullable;

import com.joshiegemfinder.synchronisedblockstates.common.client.util.PropertyBuckets.PropertyClassBucket;
import com.joshiegemfinder.synchronisedblockstates.common.client.util.PropertyBuckets.PropertyNameBucket;
import com.joshiegemfinder.synchronisedblockstates.common.client.util.PropertyBuckets.PropertyWrapper;
import com.joshiegemfinder.synchronisedblockstates.common.network.util.NetworkedPropertyRegistry;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Given two {@linkplain PropertyBuckets} ("client" and "server"), creates a two-way mapping between them.
 * 
 * Creates mappings between:
 * * Matching property buckets (same name and class on both client and server)
 * * Properties and their buckets on the same side
 * * Properties and their buckets on the other side (i.e. properties they can be matched with)
 * 
 * And keeps track of:
 * * Client-only properties
 * * Server-only properties
 * * Property comparison results (so duplicate comparisons will not require recalculation)
 */
public final class PropertyBucketMapping {

	/**
	 * Stores information about a "bucket" of properties on a single side of the mapping.
	 */
	public static record PropertyBucket(int classIndex, int nameIndex, int[] fastPropertyIndexes, ObjectList<PropertyWrapper> properties) {
		public static int[] toIntArray(ObjectList<PropertyWrapper> properties) {
			final int size = properties.size();
			int[] list = new int[size];

			for(int i = 0; i < size; ++i) {
				list[i] = properties.get(i).propertyIndex();
			}
			
			return list;
		}
		
		public PropertyBucket(int classIndex, int nameIndex, ObjectList<PropertyWrapper> properties) {
			this(
					classIndex,
					nameIndex,
					toIntArray(properties),
					new ObjectImmutableList<>(properties) // TODO See if List.copyOf(...) is better
				);
		}
	}
	
	/**
	 * Mapping between matching property buckets.
	 * The buckets exist and are non-empty on both the "client" and "server" sides.
	 * Both client and server buckets:
	 * * Share the same class
	 * * Share the same name
	 * 
	 * According to {@linkplain Property}, this makes them equal
	 */
	public static record PropertyBucketPair(String runtimeClassName, String propertyName, PropertyBucket clientBucket, PropertyBucket serverBucket) {
		
	}

	public final IntSet clientOnlyProperties;
	public final IntSet serverOnlyProperties;
//	public final PropertyBucket[] clientPropertyToServerBucket;
//	public final PropertyBucket[] serverPropertyToClientBucket;
	public final PropertyBucketPair[] clientPropertyToBucketPair;
	public final PropertyBucketPair[] serverPropertyToBucketPair;

	public final int[] clientClassIndexToServerClassIndex;
	public final Int2IntMap clientNameIndexToServerNameIndex;
	
	public final int[] serverClassIndexToClientClassIndex;
	public final Int2IntMap serverNameIndexToClientNameIndex;
	
	public static record PropertyMapResult(int[] src2DstClassIndexMap, Int2IntMap src2DstNameIndexMap) { }
	
	public static PropertyMapResult mapPropertyOptions(PropertyBuckets srcBuckets, PropertyBuckets dstBuckets) {

		// Fetch client and server property registries
		NetworkedPropertyRegistry srcRegistry = srcBuckets.getNetworkedRegistry();
		
		// IntSet would only slow us down, because a Reference2IntOpenHashSet should be just as fast in theory
//		IntSet visitedDstSet = new IntOpenHashSet();
		
		// Create mappings from src class name indexes --> dst class name indexes
		final int srcClassCount = srcRegistry.getClassTableSize();
		final String[] runtimeClassNames = srcRegistry.computeRuntimePropertyClasses();
		
		final int[] src2DstClassIndexMap = new int[srcClassCount];

		for(int srcClassIndex = 0; srcClassIndex < srcClassCount; ++srcClassIndex) {
			String srcClassName = runtimeClassNames[srcClassIndex];
			int dstClassIndex = dstBuckets.getRuntimePropertyClassIndex(srcClassName);
			src2DstClassIndexMap[srcClassIndex] = dstClassIndex;
		}

		// Create mappings from src property name indexes --> dst property name indexes
		final IntSet srcNameIndexes = srcBuckets.getPropertyNameIndexSet();
		final int srcNameCount = srcNameIndexes.size();
		final String[] stringTable = srcRegistry.getStringTable();
		
		final Int2IntOpenHashMap src2DstNameIndexMap = new Int2IntOpenHashMap(srcNameCount);
		src2DstNameIndexMap.defaultReturnValue(-1);

		for(int srcStringIndex : srcNameIndexes) {
			String srcString = stringTable[srcStringIndex];
			int dstStringIndex = dstBuckets.getPropertyNameIndex(srcString);
			if(dstStringIndex != -1) {
				src2DstNameIndexMap.put(srcStringIndex, dstStringIndex);
			}
		}
		
		// Trim map for best performance
		src2DstNameIndexMap.trim();
		
		// Return results
		return new PropertyMapResult(src2DstClassIndexMap, src2DstNameIndexMap);
	}
	
	protected final void fillBucketMappings(
			PropertyBuckets clientBuckets, PropertyBuckets serverBuckets,
//			PropertyBucket[] clientPropertyToServerBucket, PropertyBucket[] serverPropertyToClientBucket,
			PropertyBucketPair[] clientPropertyToBucketPair, PropertyBucketPair[] serverPropertyToBucketPair,
			IntSet clientOnlyProperties, IntSet serverOnlyProperties
	) {
		final int clientClassCount = clientBuckets.getClassBucketCount();
		final int serverClassCount = serverBuckets.getClassBucketCount();

		final String[] clientClassTable = clientBuckets.getNetworkedRegistry().computeRuntimePropertyClasses();
		final String[] clientStringTable = clientBuckets.getNetworkedRegistry().getStringTable();
		
		final int[] clientClassIndexToServerClassIndex = this.clientClassIndexToServerClassIndex;
		final Int2IntMap clientNameIndexToServerNameIndex = this.clientNameIndexToServerNameIndex;

		final int[] serverClassIndexToClientClassIndex = this.serverClassIndexToClientClassIndex;
		final Int2IntMap serverNameIndexToClientNameIndex = this.serverNameIndexToClientNameIndex;

		final PropertyClassBucket[] clientClassBuckets = clientBuckets.getClassBuckets();
		final PropertyClassBucket[] serverClassBuckets = serverBuckets.getClassBuckets();
		
		// Initial mapping:
		// * Client-only properties
		// * Client & server properties
		// * Server-only properties that have a dual-sided class
		for(int clientClassIndex = 0; clientClassIndex < clientClassCount; ++clientClassIndex) {
			final PropertyClassBucket clientClassBucket = clientClassBuckets[clientClassIndex];
			final int serverClassIndex = clientClassIndexToServerClassIndex[clientClassIndex];
			
			// If the property class doesn't exist on the server, these are all client-only properties
			if(serverClassIndex < 0) {
				final Int2ObjectMap<PropertyNameBucket> clientNameBuckets = clientClassBucket.nameBuckets();
//				clientNameBuckets.values().stream()
//					.flatMap(t -> t.properties().stream())
//					.mapToInt(PropertyWrapper::propertyIndex)
//					.forEach(clientOnlyProperties::add);
				for(final PropertyNameBucket clientNameBucket : clientNameBuckets.values()) {
					for(final PropertyWrapper clientProperty : clientNameBucket.properties()) {
						final int clientPropertyIndex = clientProperty.propertyIndex();
						clientOnlyProperties.add(clientPropertyIndex);
					}
				}
				continue;
			}
			
			// The property class exists on both client and server
			final PropertyClassBucket serverClassBucket = serverClassBuckets[serverClassIndex];
			
			Int2ObjectMap<PropertyNameBucket> clientNameBuckets = clientClassBucket.nameBuckets();
			
			// Find client-name-only and dual-sided property buckets
			for(final var clientPropertyEntry : clientNameBuckets.int2ObjectEntrySet()) {
				// Get client bucket with (property, name)
				final int clientNameIndex = clientPropertyEntry.getIntKey();
				final PropertyNameBucket clientNameBucket = clientPropertyEntry.getValue();

				// Get server name for this bucket
				final int serverNameIndex = clientNameIndexToServerNameIndex.get(clientNameIndex);
				final PropertyNameBucket serverNameBucket;
				
				// If there is no property bucket with this class and name, this name bucket is all client-only properties
				if(serverNameIndex < 0 || (serverNameBucket = serverClassBucket.getNameBucket(serverNameIndex)) == null) {
					for(final PropertyWrapper clientProperty : clientNameBucket.properties()) {
						final int clientPropertyIndex = clientProperty.propertyIndex();
						clientOnlyProperties.add(clientPropertyIndex);
					}
					continue;
				}
				
				// There is a bucket with this (class, name) on both the client and server
				// Create bucket pair
				PropertyBucket clientBucket = new PropertyBucket(clientClassIndex, clientNameIndex, clientNameBucket.properties());
				PropertyBucket serverBucket = new PropertyBucket(serverClassIndex, serverNameIndex, serverNameBucket.properties());

				// The class/name buckets we've been using may not have interned values
				String propertyClass = clientClassTable[clientClassIndex];
				String propertyName = clientStringTable[clientNameIndex];
				
				PropertyBucketPair bucketPair = new PropertyBucketPair(propertyClass, propertyName, clientBucket, serverBucket);

				// Fill in <client property> --> <bucket pair> map
				for(final PropertyWrapper clientProperty : clientNameBucket.properties()) {
					final int clientPropertyIndex = clientProperty.propertyIndex();
					clientPropertyToBucketPair[clientPropertyIndex] = bucketPair;
				}

				// Fill in <server property> --> <bucket pair> map
				for(final PropertyWrapper serverProperty : serverNameBucket.properties()) {
					final int serverPropertyIndex = serverProperty.propertyIndex();
					serverPropertyToBucketPair[serverPropertyIndex] = bucketPair;
				}
			}
			
			// Find server-name-only property buckets
			Int2ObjectMap<PropertyNameBucket> serverNameBuckets = serverClassBucket.nameBuckets();
			for(final var serverPropertyEntry : serverNameBuckets.int2ObjectEntrySet()) {
				// Get server bucket with (property, name)
				final int serverNameIndex = serverPropertyEntry.getIntKey();

				// Get client name index for this bucket
				final int clientNameIndex = serverNameIndexToClientNameIndex.get(serverNameIndex);
				
				// We actually only care about when the name doesn't appear on the client
				if(clientNameIndex >= 0 || clientNameBuckets.containsKey(clientNameIndex)) {
					continue;
				}
				
				// This bucket is server-only
				final PropertyNameBucket serverNameBucket = serverPropertyEntry.getValue();
				for(final PropertyWrapper serverProperty : serverNameBucket.properties()) {
					final int serverPropertyIndex = serverProperty.propertyIndex();
					serverOnlyProperties.add(serverPropertyIndex);
				}
			}
		}

		// Second mapping:
		// * Server-only properties that DON'T have a dual-sided class
		for(int serverClassIndex = 0; serverClassIndex < serverClassCount; ++serverClassIndex) {
			final int clientClassIndex = serverClassIndexToClientClassIndex[serverClassIndex];
			
			// We only care about when this property class is server-only
			// Other server-only properties have already been handled
			if(clientClassIndex >= 0) {
				continue;
			}
			
			// Mark all of these properties as server-only
			final PropertyClassBucket serverClassBucket = serverClassBuckets[serverClassIndex];
			for(final PropertyNameBucket serverNameBucket : serverClassBucket.nameBuckets().values()) {
				for(final PropertyWrapper serverProperty : serverNameBucket.properties()) {
					final int serverPropertyIndex = serverProperty.propertyIndex();
					serverOnlyProperties.add(serverPropertyIndex);
				}
			}
		}
	}
	
	// Optimises an IntHashSet by maybe making it linear at small sizes
	// A linear (array) set is faster than a hash set at small sizes (<= 16)
	public static IntSet optimiseIntHashSet(IntOpenHashSet intHashSet) {
		if(intHashSet.size() <= 16) {
			return new IntArraySet(intHashSet);
		} else {
			intHashSet.trim();
			return intHashSet;
		}
	}
	
	public PropertyBucketMapping(PropertyBuckets clientBuckets, PropertyBuckets serverBuckets) {

		// Fetch client and server property registries
		NetworkedPropertyRegistry clientRegistry = clientBuckets.getNetworkedRegistry();
		NetworkedPropertyRegistry serverRegistry = serverBuckets.getNetworkedRegistry();

		// Calculate client -> server mappings for property classes and property names
		{
			PropertyMapResult clientToServerMapResult = mapPropertyOptions(clientBuckets, serverBuckets);
			this.clientClassIndexToServerClassIndex = clientToServerMapResult.src2DstClassIndexMap();
			this.clientNameIndexToServerNameIndex = clientToServerMapResult.src2DstNameIndexMap();
		}

		// Calculate server -> client mappings for property classes and property names
		{
			PropertyMapResult serverToClientMapResult = mapPropertyOptions(serverBuckets, clientBuckets);
			this.serverClassIndexToClientClassIndex = serverToClientMapResult.src2DstClassIndexMap();
			this.serverNameIndexToClientNameIndex = serverToClientMapResult.src2DstNameIndexMap();
		}
		
		final int clientPropertyCount = clientRegistry.getPropertyTableSize();
		final int serverPropertyCount = serverRegistry.getPropertyTableSize();
		
//		// Initialize <property> --> <bucket on other side> map
//		PropertyBucket[] clientPropertyToServerBucket = new PropertyBucket[clientPropertyCount];
//		PropertyBucket[] serverPropertyToClientBucket = new PropertyBucket[serverPropertyCount];
		// Initialize <property> --> <bucket pair> map
		PropertyBucketPair[] clientPropertyToBucketPair = new PropertyBucketPair[clientPropertyCount];
		PropertyBucketPair[] serverPropertyToBucketPair = new PropertyBucketPair[serverPropertyCount];
		
		// Initialize single-side property maps
		final IntOpenHashSet clientOnlyProperties = new IntOpenHashSet(clientPropertyCount);
		final IntOpenHashSet serverOnlyProperties = new IntOpenHashSet(serverPropertyCount);

		this.fillBucketMappings(
				clientBuckets, serverBuckets,
//				clientPropertyToServerBucket, serverPropertyToClientBucket,
				clientPropertyToBucketPair, serverPropertyToBucketPair,
				clientOnlyProperties, serverOnlyProperties
			);
		
//		// Set <property> --> <bucket on other side> map
//		this.clientPropertyToServerBucket = clientPropertyToServerBucket;
//		this.serverPropertyToClientBucket = serverPropertyToClientBucket;
		// Set <property> --> <bucket pair> map
		this.clientPropertyToBucketPair = clientPropertyToBucketPair;
		this.serverPropertyToBucketPair = serverPropertyToBucketPair;

		// Set single-side maps
		this.clientOnlyProperties = IntSets.unmodifiable(optimiseIntHashSet(clientOnlyProperties));
		this.serverOnlyProperties = IntSets.unmodifiable(optimiseIntHashSet(serverOnlyProperties));
	}
	
	public boolean isClientOnlyProperty(int clientPropertyIndex) {
		return this.clientPropertyToBucketPair[clientPropertyIndex] == null;
	}
	
	public boolean isServerOnlyProperty(int serverPropertyIndex) {
		return this.serverPropertyToBucketPair[serverPropertyIndex] == null;
	}

	@Nullable
	public PropertyBucketPair getClientPropertyBucketPair(int clientPropertyIndex) {
		return this.clientPropertyToBucketPair[clientPropertyIndex];
	}

	@Nullable
	public PropertyBucketPair getServerPropertyBucketPair(int serverPropertyIndex) {
		return this.clientPropertyToBucketPair[serverPropertyIndex];
	}
	
	public IntSet getAllClientOnlyProperties() {
		return this.clientOnlyProperties;
	}

	public IntSet getAllServerOnlyProperties() {
		return this.serverOnlyProperties;
	}
}
