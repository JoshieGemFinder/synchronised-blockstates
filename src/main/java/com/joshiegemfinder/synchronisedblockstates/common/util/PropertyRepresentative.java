package com.joshiegemfinder.synchronisedblockstates.common.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;

import com.joshiegemfinder.synchronisedblockstates.common.service.ClassMappingService;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.state.properties.Property;

public record PropertyRepresentative(String name, String propertyClass, String[] allowedValues) {

	// String::intern has some issues, apparently
	public static final ObjectOpenHashSet<String> STRING_INTERNER = new ObjectOpenHashSet<String>();
	public static final Object2ObjectOpenHashMap<PropertyRepresentative.InternKey, PropertyRepresentative> PROPERTY_INTERNER = new Object2ObjectOpenHashMap<PropertyRepresentative.InternKey, PropertyRepresentative>();
	public static final Reference2ObjectOpenHashMap<Property<?>, PropertyRepresentative> PROPERTY_MAP = new Reference2ObjectOpenHashMap<Property<?>, PropertyRepresentative>();

	public static int compare(Property<?> property1, Property<?> property2) {
		return String.CASE_INSENSITIVE_ORDER.compare(property1.getName(), property2.getName());
	}

	public static String internString(String string) {
//		return string.intern();
		return STRING_INTERNER.addOrGet(string);
	}
	
	public static void internStrings(String[] stringTable) {
		final int stringTableSize = stringTable.length;
		for(int i = 0; i < stringTableSize; ++i) {
			stringTable[i] = internString(stringTable[i]);
		}
	}
	
	@Deprecated
	public PropertyRepresentative(String name, String propertyClass, String[] allowedValues) {
		this.name = name;
		this.propertyClass = propertyClass;
		this.allowedValues = allowedValues;
	}

	@Override
	public final String toString() {
		return String.format("PropertyRepresentative[name=%s, propertyClass=%s, allowedValues=%s]", this.name, this.propertyClass, Arrays.toString(this.allowedValues));
//		return String.format("PropertyRepresentative[name=%s, allowedValues=%s]", this.name, Arrays.toString(this.allowedValues));
	}
	
	public static PropertyRepresentative of(Property<?> property) {
		return PROPERTY_MAP.computeIfAbsent(property, (Property<?> key) -> PropertyRepresentative.create(key.getName(), key.getValueClass().getName(), getPropertyNameArray(key)));
	}
	
	public static <T extends Comparable<T>> String[] getPropertyNameArray(Property<T> property) {
//		return (String[])(property.getPossibleValues().stream().map(v -> property.getName(v)).toArray());
		final Collection<T> values = property.getPossibleValues();
		final int count = values.size();
		final String[] names = new String[count];
		int i = 0;
		for(T val : values) {
			names[i++] = property.getName(val);
		}
		return names;
	}

	public static PropertyRepresentative create(String name, String propertyClass, String[] allowedValues) {
		return PROPERTY_INTERNER.computeIfAbsent(new InternKey(name, propertyClass, allowedValues), (PropertyRepresentative.InternKey key) -> {
			key.intern();
			return new PropertyRepresentative(key.name, key.propertyClass, key.allowedValues);
		});
	}

	public static void encode(FriendlyByteBuf buf, final PropertyRepresentative property) {
		buf.writeUtf(property.name);
		String propertyClass = property.propertyClass;
//		String networkClass = propertyClass;
		String networkClass = ClassMappingService.INSTANCE.convertRuntimeToNetworkMappings(propertyClass);
		buf.writeUtf(networkClass);
		final String[] allowedValues = property.allowedValues;
		final int allowedValueCount = allowedValues.length;
		buf.writeVarInt(allowedValueCount);
		for(int i = 0; i < allowedValueCount; ++i) {
			buf.writeUtf(allowedValues[i]);
		}
	}

	public static PropertyRepresentative decode(FriendlyByteBuf buf) {
		String name = buf.readUtf();	
		String networkClass = buf.readUtf();
//		String propertyClass = networkClass;
		String propertyClass = ClassMappingService.INSTANCE.convertNetworkToRuntimeMappings(networkClass);
		final int allowedValueCount = buf.readVarInt();
		final String[] allowedValues = new String[allowedValueCount];
		for(int i = 0; i < allowedValueCount; ++i) {
			allowedValues[i] = buf.readUtf();
		}
		
		return PropertyRepresentative.create(name, propertyClass, allowedValues);
	}
	
	@Override
	public final int hashCode() {
		return name.hashCode() * 31 + propertyClass.hashCode();
//		return name.hashCode();
	}
	
	@Override
	public final boolean equals(Object arg0) {
		// we interned the name and property class so we can use == here
		return arg0 instanceof PropertyRepresentative property && (this.name == property.name && /*this.propertyClass == property.propertyClass &&*/ Arrays.equals(this.allowedValues, property.allowedValues));
	}
	
	public static final class InternKey {

		private boolean hasInterned = false;
		private String name;
		private String propertyClass;
		private String[] allowedValues;
		
		public InternKey(String name, String propertyClass, String[] allowedValues) {
			this.name = name;
			this.propertyClass = propertyClass;
			
			String[] sortedAllowedValues = allowedValues.clone();
			sortValues(sortedAllowedValues);
			
			this.allowedValues = sortedAllowedValues;
		}

		public static void sortValues(String[] values) {
			Arrays.sort(values, String.CASE_INSENSITIVE_ORDER);
		}
		
		public static <T> void sortValues(T[] values, Function<T, String> keyExtractor) {
			Arrays.sort(values, Comparator.comparing(keyExtractor, String.CASE_INSENSITIVE_ORDER));
		}
		
		public void intern() {
			if(this.hasInterned) return; 
			this.name = internString(this.name);
			this.propertyClass = internString(this.propertyClass);
			internStrings(this.allowedValues);
			this.hasInterned = true;
		}
		
		@Override
		public final int hashCode() {
			return name.hashCode() * 31 + propertyClass.hashCode();
//			return name.hashCode();
		}
		
		@Override
		public final boolean equals(Object arg0) {
			return arg0 instanceof InternKey property && (Objects.equals(this.name, property.name) && /*Objects.equals(this.propertyClass, property.propertyClass) &&*/ Arrays.equals(this.allowedValues, property.allowedValues));
		}
	}
}
