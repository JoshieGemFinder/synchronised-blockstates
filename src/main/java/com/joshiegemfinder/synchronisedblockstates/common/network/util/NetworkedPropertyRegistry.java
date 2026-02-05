package com.joshiegemfinder.synchronisedblockstates.common.network.util;

import java.util.function.ToIntFunction;

import com.joshiegemfinder.synchronisedblockstates.common.service.ClassMappingService;
import com.joshiegemfinder.synchronisedblockstates.common.util.PropertyRepresentative;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.network.FriendlyByteBuf;

/**
 * <p>
 *     Storage class that breaks down an array of {@link PropertyRepresentative PropertyRepresentatives} into a form
 *     that is better suited to being sent as packets
 * </p>
 * <p>
 *     Properties are broken down into:
 *     <ol>
 *         <li>A property class table that stores the network-remapped property classes, so they can be bulk runtime-remapped on the client</li>
 *         <li>A general string table that stores the values of both property names and allowed values</li>
 *         <li>An int-only version of the properties, that index the property class and string tables</li>
 *     </ol>
 * </p>
 */
public record NetworkedPropertyRegistry(
		String[] remappedPropertyClasses,
		String[] stringTable,
		NetworkedProperty[] properties
	) {
	
	// Note: it would be possible to get rid of NetworkedProperty and instead replace it with just a really big array of integers
	
	public static NetworkedPropertyRegistry create(PropertyRepresentative[] propertyRegistry) {
		final int propertyCount = propertyRegistry.length;
		
		// Reference2Int because PropertyRepresentative interns its property class
		final ObjectArrayList<String> remappedPropertyClassesList = new ObjectArrayList<>(1024);
		final Reference2IntOpenHashMap<String> remappedPropertyClassInterner = new Reference2IntOpenHashMap<>(1024);
		
		// Reference2Int because PropertyRepresentative interns all of its values
		final ObjectArrayList<String> stringTableList = new ObjectArrayList<>(1024);
		final Reference2IntOpenHashMap<String> stringTableInterner = new Reference2IntOpenHashMap<>(1024);

		// Lambda metafactory
		final ToIntFunction<String> getOrAddRemappedPropertyIndex = (String propertyClass) -> remappedPropertyClassInterner.computeIfAbsent(propertyClass, (String key) -> {
			// Get the index of the value
			final int index = remappedPropertyClassesList.size();
			// Convert to network mappings and add to remapped property classes list
			String networkPropertyClass = ClassMappingService.INSTANCE.convertRuntimeToNetworkMappings(propertyClass);
			remappedPropertyClassesList.add(networkPropertyClass);
			// Return index in property classes list
			return index;
		});
		
		final ToIntFunction<String> getOrAddStringTableValue = (String string) -> stringTableInterner.computeIfAbsent(string, (String key) -> {
			// Get the index of the value
			final int index = stringTableList.size();
			// Add the value to the ordered list
			stringTableList.add(key);
			// Return index in property classes list
			return index;
		});
		
		final NetworkedProperty[] properties = new NetworkedProperty[propertyCount];

		for(int i = 0; i < propertyCount; ++i) {
			final PropertyRepresentative property = propertyRegistry[i];
			
			// Remap and then intern the property class
			int propertyClassIndex = getOrAddRemappedPropertyIndex.applyAsInt(property.propertyClass());
			
			// Intern the name
			int nameIndex = getOrAddStringTableValue.applyAsInt(property.name());
			
			// Intern all of the property values
			String[] allowedValues = property.allowedValues();
			final int allowedValueCount = allowedValues.length;
			int[] allowedValueIndices = new int[allowedValueCount];
			for(int j = 0; j < allowedValueCount; ++j) {
				allowedValueIndices[j] = getOrAddStringTableValue.applyAsInt(allowedValues[j]);
			}
			
			// Create the networked properties
			properties[i] = new NetworkedProperty(nameIndex, propertyClassIndex, allowedValueIndices);
		}
		
		return new NetworkedPropertyRegistry(
				remappedPropertyClassesList.toArray(new String[0]), 
				stringTableList.toArray(new String[0]),
				properties
			);
	}
	
	public final int getClassTableSize() {
		return this.remappedPropertyClasses.length;
	}

	public final int getStringTableSize() {
		return this.stringTable.length;
	}
	
	public final String[] getRemappedPropertyClasses() {
		return this.remappedPropertyClasses;
	}
	
	public final String[] getStringTable() {
		return this.stringTable;
	}
	
	public final NetworkedProperty[] getProperties() {
		return this.properties;
	}
	
	public final String[] computeRuntimePropertyClasses() {
		// Get the property class table we need to remap
		final String[] remappedPropertyClasses = this.remappedPropertyClasses;
		final int remappedPropertyClassesLength = remappedPropertyClasses.length;
		
		// Create our output array
		final String[] runtimePropertyClasses = new String[remappedPropertyClassesLength];
		
		// Remap all network mappings to runtime mappings
		for(int i = 0; i < remappedPropertyClassesLength; ++i) {
			final String networkClassName = remappedPropertyClasses[i];
			
			// Remap to runtime mappings and store in output array
			runtimePropertyClasses[i] = ClassMappingService.INSTANCE.convertNetworkToRuntimeMappings(networkClassName);
		}
		
		// Return output array
		return runtimePropertyClasses;
	}
	
	public final PropertyRepresentative[] compileProperties() {
		PropertyRepresentative[] compiledProperties = new PropertyRepresentative[this.properties.length];
		
		this.compilePropertiesInto(compiledProperties);
		
		return compiledProperties;
	}
	
	public final void compilePropertiesInto(PropertyRepresentative[] compiledProperties) {
		// Get the property class table, already remapped to runtime mappings
		final String[] runtimePropertyClasses = this.computeRuntimePropertyClasses();
		
		// Get the string table and property table we'll need to decode
		final String[] stringTable = this.stringTable;
		final NetworkedProperty[] networkedProperties = this.properties;
		
		final int propertyCount = networkedProperties.length;
		
		for(int i = 0; i < propertyCount; ++i) {
			NetworkedProperty networkedProperty = networkedProperties[i];
			
			String name = stringTable[networkedProperty.nameIndex()];
			String runtimePropertyClass = runtimePropertyClasses[networkedProperty.propertyClassIndex()];
			
			int[] allowedValueIndices = networkedProperty.allowedValueIndices();
			final int allowedValueCount = allowedValueIndices.length;
			
			String[] allowedValues = new String[allowedValueCount];
			for(int valueIndex = 0; valueIndex < allowedValueCount; ++valueIndex) {
				int stringTableIndex = allowedValueIndices[valueIndex];
				allowedValues[valueIndex] = stringTable[stringTableIndex];
			}
			
			compiledProperties[i] = PropertyRepresentative.create(name, runtimePropertyClass, allowedValues);
		}
	}
	
	public static void encode(FriendlyByteBuf buf, final NetworkedPropertyRegistry propertyRegistry) {
		final String[] remappedPropertyClasses = propertyRegistry.remappedPropertyClasses();
		final String[] stringTable = propertyRegistry.stringTable();
		final NetworkedProperty[] properties = propertyRegistry.properties();

		// Write remapped property class table
		final int remappedPropertyClassesLength = remappedPropertyClasses.length;
		buf.writeVarInt(remappedPropertyClassesLength);
		for(int i = 0; i < remappedPropertyClassesLength; ++i) {
			buf.writeUtf(remappedPropertyClasses[i]);
		}

		// Write string table
		final int stringTableLength = stringTable.length;
		buf.writeVarInt(stringTableLength);
		for(int i = 0; i < stringTableLength; ++i) {
			buf.writeUtf(stringTable[i]);
		}

		// Write property index table
		final int propertiesLength = properties.length;
		buf.writeVarInt(propertiesLength);
		for(int i = 0; i < propertiesLength; ++i) {
			NetworkedProperty.encode(buf, properties[i]);
		}
	}
	
	public static NetworkedPropertyRegistry decode(FriendlyByteBuf buf) {
		// Decode remapped property class table
		final int remappedPropertyClassesLength = buf.readVarInt();
		final String[] remappedPropertyClasses = new String[remappedPropertyClassesLength];
		for(int i = 0; i < remappedPropertyClassesLength; ++i) {
			remappedPropertyClasses[i] = buf.readUtf();
		}
		
		// Decode string table
		final int stringTableLength = buf.readVarInt();
		final String[] stringTable = new String[stringTableLength];
		for(int i = 0; i < stringTableLength; ++i) {
			stringTable[i] = buf.readUtf();
		}
		
		// Decode property index table
		final int propertiesLength = buf.readVarInt();
		final NetworkedProperty[] properties = new NetworkedProperty[propertiesLength];
		for(int i = 0; i < propertiesLength; ++i) {
			properties[i] = NetworkedProperty.decode(buf);
		}
		
		// Create our registry
		return new NetworkedPropertyRegistry(remappedPropertyClasses, stringTable, properties);
	}
}
