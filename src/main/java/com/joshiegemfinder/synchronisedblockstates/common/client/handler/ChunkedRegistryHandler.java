package com.joshiegemfinder.synchronisedblockstates.common.client.handler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import com.joshiegemfinder.synchronisedblockstates.common.network.util.NetworkedProperty;
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry;
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry.ChunkedRegistryDecoder;
import com.joshiegemfinder.synchronisedblockstates.common.util.RegistryBlockInfoWrapper;

public class ChunkedRegistryHandler {

	public static final Map<UUID, ChunkedRegistryDecoder> ACTIVE_DECODERS = new ConcurrentHashMap<UUID, ChunkedRegistryDecoder>();

	public static ChunkedRegistryDecoder getDecoder(UUID uuid) {
		return ACTIVE_DECODERS.get(uuid);
	}

	public static boolean ifDecoderPresent(UUID uuid, Consumer<ChunkedRegistryDecoder> decoderConsumer) {
		ChunkedRegistryDecoder decoder = getDecoder(uuid);
		if(decoder == null) {
			return false;
		} else {
			decoderConsumer.accept(decoder);
			return true;
		}
	}

	public static boolean ifDecoderPresentOptional(UUID uuid, Function<ChunkedRegistryDecoder, Boolean> decoderFunction) {
		ChunkedRegistryDecoder decoder = getDecoder(uuid);
		if(decoder == null) {
			return false;
		} else {
			return decoderFunction.apply(decoder);
		}
	}
	
	public static void clearDecoders() {
		ACTIVE_DECODERS.clear();
	}

	public static boolean startDecoding(UUID uuid, int classTableSize, int stringTableSize, int totalPropertyCount, int totalBlockCount) {
		try {
			ChunkedRegistryDecoder decoder = ACTIVE_DECODERS.compute(uuid, (t, u) -> {
				if(u != null) {
					// TODO change before release (yeah right nerd that'll totally happen)
					throw new IllegalArgumentException("Unexpected attempt to start decoding on a currently in-use UUID");
				} else {
//					System.out.println("decode start: props = %d, blocks = %d".formatted(totalPropertyCount, totalBlockCount));
					return BlockInfoRegistry.startChunkDecoding(uuid, classTableSize, stringTableSize, totalPropertyCount, totalBlockCount);
				}
			});
			return decoder != null;
		} catch(RuntimeException e) {
			return false;
		}
	}

	public static boolean acceptProperties(UUID uuid, int propertyOffset, NetworkedProperty[] properties) {
		return ifDecoderPresentOptional(uuid, (ChunkedRegistryDecoder decoder) -> {
			if(!decoder.isFinished()) {
				decoder.acceptProperties(propertyOffset, properties);
				return true;
			} else {
				return false;
			}
		});
	}

	public static boolean acceptPropertyClasses(UUID uuid, int tableOffset, String[] propertyClasses) {
		return ifDecoderPresentOptional(uuid, (ChunkedRegistryDecoder decoder) -> {
			if(!decoder.isFinished()) {
				decoder.acceptPropertyClasses(tableOffset, propertyClasses);
				return true;
			} else {
				return false;
			}
		});
	}

	public static boolean acceptPropertyStringTable(UUID uuid, int tableOffset, String[] stringTableData) {
		return ifDecoderPresentOptional(uuid, (ChunkedRegistryDecoder decoder) -> {
			if(!decoder.isFinished()) {
				decoder.acceptPropertyStringTable(tableOffset, stringTableData);
				return true;
			} else {
				return false;
			}
		});
	}
	
	public static boolean acceptBlockInfo(UUID uuid, int blockInfoOffset, RegistryBlockInfoWrapper[] blockInfoArray) {
		return ifDecoderPresentOptional(uuid, (ChunkedRegistryDecoder decoder) -> {
			if(!decoder.isFinished()) {
				decoder.acceptBlocks(blockInfoOffset, blockInfoArray);
				return true;
			} else {
				return false;
			}
		});
	}
	
	public static BlockInfoRegistry build(UUID uuid) {
		ChunkedRegistryDecoder decoder = getDecoder(uuid);
		return decoder != null ? decoder.build() : null;
	}
	
}
