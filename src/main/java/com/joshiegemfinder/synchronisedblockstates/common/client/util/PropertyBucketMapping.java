package com.joshiegemfinder.synchronisedblockstates.common.client.util;

import com.joshiegemfinder.synchronisedblockstates.common.client.util.PropertyBuckets.PropertyWrapper;
import com.joshiegemfinder.synchronisedblockstates.common.network.util.NetworkedPropertyRegistry;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
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
	public static record PropertyBucket(int classIndex, int nameIndex, ObjectList<PropertyWrapper> properties) {
		public PropertyBucket(int classIndex, int nameIndex, ObjectList<PropertyWrapper> properties) {
			this.classIndex = classIndex;
			this.nameIndex = nameIndex;
			// TODO See if List.copyOf(...) is better
			this.properties = new ObjectImmutableList<>(properties);
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
	public final PropertyBucket[] clientPropertyToServerBucket;
	public final PropertyBucket[] serverPropertyToClientBucket;

	public final int[] clientClassIndexToServerClassIndex;
	public final Int2IntMap clientNameIndexToServerNameIndex;
	
	public final int[] serverClassIndexToClientClassIndex;
	public final Int2IntMap serverNameIndexToClientNameIndex;
	
	public static void mapClassIndex(PropertyBuckets srcBuckets, PropertyBuckets dstBuckets) {
		// Fetch src and dst property registries
		NetworkedPropertyRegistry srcRegistry = srcBuckets.getNetworkedRegistry();
		NetworkedPropertyRegistry dstRegistry = dstBuckets.getNetworkedRegistry();
	}
	
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
		
		// Initialize <property> --> <bucket on other side> map
		this.clientPropertyToServerBucket = new PropertyBucket[clientPropertyCount];
		this.serverPropertyToClientBucket = new PropertyBucket[serverPropertyCount];
		
		// Initialize single-side property map
		final IntOpenHashSet clientOnlyProperties = new IntOpenHashSet(clientPropertyCount);
		final IntOpenHashSet serverOnlyProperties = new IntOpenHashSet(serverPropertyCount);

		clientOnlyProperties.trim();
		this.clientOnlyProperties = IntSets.unmodifiable(clientOnlyProperties);
		serverOnlyProperties.trim();
		this.serverOnlyProperties = IntSets.unmodifiable(serverOnlyProperties);
		
	}
}
