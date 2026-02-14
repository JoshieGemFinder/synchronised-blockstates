package com.joshiegemfinder.synchronisedblockstates.common.client.util;

/**
 * Given two {@linkplain PropertyBuckets} (client and server), creates a two-way mapping between them.
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

}
