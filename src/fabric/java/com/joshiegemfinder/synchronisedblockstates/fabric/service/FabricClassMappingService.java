package com.joshiegemfinder.synchronisedblockstates.fabric.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.google.auto.service.AutoService;
import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.service.ClassMappingService;
import com.joshiegemfinder.synchronisedblockstates.common.util.MappingCollector;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

@AutoService(ClassMappingService.class)
public class FabricClassMappingService implements ClassMappingService {

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private volatile boolean initialized = false;
	private final Object2ObjectOpenHashMap<String, String> runtimeToNetworkMappings = new Object2ObjectOpenHashMap<>(8192);
	private final Object2ObjectOpenHashMap<String, String> networkToRuntimeMappings = new Object2ObjectOpenHashMap<>(8192);
	
	protected void addMapping(String runtimeClassName, String networkClassName) {
		this.runtimeToNetworkMappings.put(runtimeClassName, networkClassName);
		this.networkToRuntimeMappings.put(networkClassName, runtimeClassName);
	}
	
	protected boolean loadMappings() throws IOException {
		Properties mappingProperties = new Properties();
		
		try(InputStream propertiesStream = FabricClassMappingService.class.getResourceAsStream("/mappings/fabric-mappings.properties")) {
			mappingProperties.load(propertiesStream);
		}
		
		final String runtimeNamespace = mappingProperties.getProperty("runtimeNamespace");
		final String networkNamespace = mappingProperties.getProperty("networkNamespace");
		
		if(runtimeNamespace == null || networkNamespace == null) {
			SynchronisedBlockstates.LOGGER.warn("Fabric class remapper: null runtimeNamespace/networkNamespace is not valid");
			return false;
		}
		
		MappingCollector collector = new MappingCollector(runtimeNamespace, networkNamespace);
		
		try(InputStream mappings = FabricClassMappingService.class.getResourceAsStream("/mappings/fabric-mappings.tiny")) {
			collector.readMappings(mappings, this::addMapping);
		}
		
		return true;
	}
	
	@Override
	public void initializeMappings() {
		Lock writeLock = this.lock.writeLock();
		writeLock.lock();
		SynchronisedBlockstates.LOGGER.info("Initializing fabric class remapper!");
		try {
			this.initialized = false;
			this.runtimeToNetworkMappings.clear();
			this.networkToRuntimeMappings.clear();
			
			try {
				this.initialized = this.loadMappings();

				SynchronisedBlockstates.LOGGER.info("Fabric class mapper initialization successful: {}", this.initialized);
			} catch(IOException e) {
				SynchronisedBlockstates.LOGGER.warn("Failed to load fabric class mapper!", e);
			}
		} finally {
			writeLock.unlock();
		}
	}

	@Override
	public String convertRuntimeToNetworkMappings(String className) {
		Lock readLock = this.lock.readLock();
		readLock.lock();
		try {
			if(!this.initialized) {
				return className;
			}
			return this.runtimeToNetworkMappings.getOrDefault(className, className);
		} finally {
			readLock.unlock();
		}
	}

	@Override
	public String convertNetworkToRuntimeMappings(String className) {
		Lock readLock = this.lock.readLock();
		readLock.lock();
		try {
			if(!this.initialized) {
				return className;
			}
			return this.networkToRuntimeMappings.getOrDefault(className, className);
		} finally {
			readLock.unlock();
		}
	}

}
