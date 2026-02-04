package com.joshiegemfinder.synchronisedblockstates.common.service;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import com.google.common.collect.ImmutableList;
import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;

import net.minecraft.Util;

public interface ClassMappingService {
	
	public static final ClassMappingService INSTANCE = Util.make(() -> {
		final ServiceLoader<ClassMappingService> loader = ServiceLoader.load(ClassMappingService.class);

		List<ClassMappingService> list = new ArrayList<>();
		for(ClassMappingService classMapper : loader) {
			list.add(classMapper);
		}
		
		if(list.size() > 1) {
			return new Union(list);
		} else if(list.size() == 1) {
			return list.get(0);
		}
		
		SynchronisedBlockstates.LOGGER.warn("Could not find a class remapper!");
		return new Empty();
	});

	/**
	 * Lets the remapper do any setup necessary
	 */
	public void initializeMappings();
	
	/**
	 * <p>Converts runtime mappings to the mappings used for networking (mojmaps)</p>
	 * <p>For example, it might convert {@code net.minecraft.class_2361} to {@code net.minecraft.core.IdMapper} if you're using intermediary at runtime</p>
	 * @param className The name of a class, using the current runtime mappings
	 * @return The class name in Mojang mappings, or {@code className} if it couldn't find a mapping for it
	 */
	public String convertRuntimeToNetworkMappings(String className);

	/**
	 * <p>Converts network mappings (mojmaps) to the current runtime mappings</p>
	 * <p>For example, it might convert {@code net.minecraft.core.IdMapper} to {@code net.minecraft.class_2361} if you're using intermediary at runtime</p>
	 * @param className The name of a class, using network mappings
	 * @return The class name in runtime mappings, or {@code className} if it couldn't find a mapping for it
	 */
	public String convertNetworkToRuntimeMappings(String className);
	
	/**
	 * Fallback for if no remappers could be found
	 */
	public static final class Empty implements ClassMappingService {

		@Override
		public void initializeMappings() {}
		
		@Override
		public String convertRuntimeToNetworkMappings(String className) {
			return className;
		}

		@Override
		public String convertNetworkToRuntimeMappings(String className) {
			return className;
		}
	}

	/**
	 * Fallback for if multiple remappers were found
	 */
	public static final class Union implements ClassMappingService {

		private List<ClassMappingService> mappers;
		
		public Union(List<ClassMappingService> mappers) {
			this.mappers = ImmutableList.copyOf(mappers);
		}
		
		@Override
		public void initializeMappings() {
			for(ClassMappingService mapper : mappers) {
				mapper.initializeMappings();
			}
		}
		
		@Override
		public String convertRuntimeToNetworkMappings(String className) {
			for(ClassMappingService mapper : mappers) {
				String mapping = mapper.convertRuntimeToNetworkMappings(className);
				if(!className.equals(mapping)) {
					return mapping;
				}
			}
			return className;
		}

		@Override
		public String convertNetworkToRuntimeMappings(String className) {
			for(ClassMappingService mapper : mappers) {
				String mapping = mapper.convertNetworkToRuntimeMappings(className);
				if(!className.equals(mapping)) {
					return mapping;
				}
			}
			return className;
		}
	}
}
