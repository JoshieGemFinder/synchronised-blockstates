package com.joshiegemfinder.synchronisedblockstates.common.client.util;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;

import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import net.minecraft.Util;
import net.minecraft.core.IdMapper;

/**
 * A mutable IdMapper wrapper, allowing us to swap the mapper being used without needing to change any references to Block.BLOCK_STATE_REGISTRY
 * @param <T>
 */
public class ProxyIdMapper<T> extends IdMapper<T> {

	protected int lastNextId = this.nextId;
	
	@Nullable
	protected IdMapper<T> sourceMapper = null;

	public ProxyIdMapper(int i) {
		super(i);
//		this.idToT = Lists.<T>newArrayListWithExpectedSize(i);
//		this.tToId = new Object2IntOpenCustomHashMap<>(i, Util.identityStrategy()); //Reference2IntOpenHashMap would be a faster implementation
//		this.tToId.defaultReturnValue(-1);
	}
	
	public ProxyIdMapper(IdMapper<T> sourceMapper) {
		super(0);
		this.setSourceMapper(sourceMapper);
	}
	
	public boolean isProxying() {
		return this.sourceMapper != null;
	}
	
	public IdMapper<T> getSourceMapper() {
		return this.sourceMapper;
	}
	
	public void setSourceMapper(@NotNull IdMapper<T> sourceMapper) {
		this.sourceMapper = Objects.requireNonNull(sourceMapper);
		this.lastNextId = this.nextId = sourceMapper.nextId;
		this.tToId = sourceMapper.tToId;
		this.idToT = sourceMapper.idToT;
	}
	
	public void removeSourceMapper() {
		this.removeSourceMapper(512);
	}
	
	public void removeSourceMapper(int newSize) {
		this.sourceMapper = null;
		this.idToT = Lists.<T>newArrayListWithExpectedSize(newSize);
		this.tToId = new Object2IntOpenCustomHashMap<>(newSize, Util.identityStrategy());
		this.tToId.defaultReturnValue(-1);
		this.lastNextId = this.nextId = 0;
	}
	
	private void trackNextIdChanges() {
		if(this.lastNextId != this.nextId) { // nextId has been unexpectedly changed by a mixin
			this.lastNextId = this.sourceMapper.nextId = this.nextId;
		} else { // otherwise, update to match any nextId changes to sourceMapper
			this.lastNextId = this.nextId = this.sourceMapper.nextId;
		}
	}
	
	@Override
	public void addMapping(T object, int i) {
		this.trackNextIdChanges();
		this.sourceMapper.addMapping(object, i);
		this.lastNextId = this.nextId = this.sourceMapper.nextId;
	}

	@Override
	public void add(T object) {
		this.trackNextIdChanges();
		this.addMapping(object, this.sourceMapper.nextId);
	}
}
