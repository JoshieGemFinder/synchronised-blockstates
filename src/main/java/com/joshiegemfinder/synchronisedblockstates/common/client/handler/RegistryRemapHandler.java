package com.joshiegemfinder.synchronisedblockstates.common.client.handler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.client.SynchronisedBlockstatesClient;
import com.joshiegemfinder.synchronisedblockstates.common.client.util.MappingUtil;
import com.joshiegemfinder.synchronisedblockstates.common.client.util.RemappingIdMapper;
import com.joshiegemfinder.synchronisedblockstates.common.network.util.ClientAckResponse;
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry;
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoWrapper;
import com.joshiegemfinder.synchronisedblockstates.common.util.PropertyRepresentative;
import com.joshiegemfinder.synchronisedblockstates.common.util.RegistryBlockInfoWrapper;
import com.joshiegemfinder.synchronisedblockstates.common.util.RegistryBlockInfoWrapper.Impl;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import net.minecraft.core.IdMapper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class RegistryRemapHandler {
	
	public static record ClientServerBlockMatch(ResourceKey<Block> resourceKey, RegistryBlockInfoWrapper.Impl clientBlockInfo, RegistryBlockInfoWrapper.Impl serverBlockInfo) {}
//	
//	public static record RegistryCompareResult(List<ResourceKey<Block>> clientOnlyKeys, List<ResourceKey<Block>> serverOnlyKeys, List<ClientServerBlockMatch> bothSidedBlocks) {
//		public static RegistryCompareResult create(BlockInfoRegistryCompareHelper clientRegistry, BlockInfoRegistryCompareHelper serverRegistry) {
	public static record RegistryCompareResult(List<RegistryBlockInfoWrapper.Impl> clientOnlyBlocks, List<RegistryBlockInfoWrapper.Impl> serverOnlyBlocks, List<ClientServerBlockMatch> bothSidedBlocks) {
		public static RegistryCompareResult create(BlockInfoRegistryCompareHelper clientRegistry, BlockInfoRegistryCompareHelper serverRegistry) {
			final int clientKeySize = clientRegistry.orderedKeys.size();
			final int serverKeySize = serverRegistry.orderedKeys.size();
			final int maxPresentSize = Math.min(clientKeySize, serverKeySize);
			
			List<ClientServerBlockMatch> bothSidedBlocks = new ArrayList<ClientServerBlockMatch>(maxPresentSize);
			List<RegistryBlockInfoWrapper.Impl> clientOnlyBlocks = new ArrayList<RegistryBlockInfoWrapper.Impl>(clientKeySize);
			
			Reference2ObjectLinkedOpenHashMap<ResourceKey<Block>, RegistryBlockInfoWrapper.Impl> serverMap = new Reference2ObjectLinkedOpenHashMap<ResourceKey<Block>, RegistryBlockInfoWrapper.Impl>(serverRegistry.blockInfoMap);
			serverMap.defaultReturnValue(null);
			
			for(Entry<ResourceKey<Block>, Impl> clientEntry : clientRegistry.blockInfoMap.entrySet()) {
				ResourceKey<Block> clientKey = clientEntry.getKey();
				Impl serverValue = serverMap.remove(clientKey);
				if(serverValue != null) {
					bothSidedBlocks.add(new ClientServerBlockMatch(clientKey, clientEntry.getValue(), serverValue));
				} else {
					clientOnlyBlocks.add(clientEntry.getValue());
				}
			}
			
			return new RegistryCompareResult(clientOnlyBlocks, new ArrayList<>(serverMap.values()), bothSidedBlocks);
		}
	}
	
	public static class BlockInfoRegistryCompareHelper {
		public final int stateCount;
		public final List<ResourceKey<Block>> orderedKeys;
		public final Reference2ObjectOpenHashMap<ResourceKey<Block>, RegistryBlockInfoWrapper.Impl> blockInfoMap;

		public BlockInfoRegistryCompareHelper(BlockInfoRegistry registry) {
			final RegistryBlockInfoWrapper.Impl[] blocks = registry.getBlocks();
			orderedKeys = new ArrayList<ResourceKey<Block>>(blocks.length);
			blockInfoMap = new Reference2ObjectOpenHashMap<ResourceKey<Block>, RegistryBlockInfoWrapper.Impl>(blocks.length);
			
			for(int i = 0; i < blocks.length; ++i) {
				final RegistryBlockInfoWrapper.Impl block = blocks[i];
				final ResourceKey<Block> key = block.getKey();
				orderedKeys.add(key);
				blockInfoMap.put(key, block);
			}
			
			this.stateCount = registry.getStateCount();
		}
	}
	
	public static void revertToVanillaBlockstates() {
		SynchronisedBlockstates.LOGGER.warn("Synchronised Blockstates did not receive a block registry from server during the expected phase, attempting to revert to vanilla mappings...");
		
		BlockInfoRegistry vanillaBlocks = SynchronisedBlockstatesClient.readVanillaBlockstates();
		
		if(vanillaBlocks != null) {
			handleRegistryReceived(vanillaBlocks);
		}
	}

//	private static void outputEntryList(FileWriter writer, List<ResourceKey<Block>> keyList) throws IOException {
	private static void outputEntryList(FileWriter writer, List<? extends BlockInfoWrapper> keyList) throws IOException {
		if(keyList.size() == 0) {
			writer.write("\tNone!\n");
		} else {
			// TODO maybe change this to write "\t\n" only once somehow for speed maybe?
			for(BlockInfoWrapper key : keyList) {
				writer.write("\t");
				writer.write(key.getKey().location().toString());
				writer.write("\n");
			}
		}
	}
	
	private static void outputSidedProperties(FileWriter writer, PropertyRepresentative[] properties, int[] missingProperties) throws IOException {
		if(missingProperties.length == 0) {
			writer.write("\t\t\tNone!\n");
		} else {
			for(final int propertyIndex : missingProperties) {
				PropertyRepresentative property = properties[propertyIndex];
				writer.write("\t\t\t");
				writer.write(property.name());
				writer.write("{ class = \"");
				writer.write(property.propertyClass());
				writer.write("\", values = ");
				writer.write(Arrays.toString(property.allowedValues()));
				writer.write(" }\n");
			}
		}
	}
	
	private static void outputStateMismatch(FileWriter writer, StateMismatchResult stateMismatch) throws IOException {
		final PropertyRepresentative[] clientProperties = stateMismatch.clientBlock().getProperties();
		final PropertyRepresentative[] serverProperties = stateMismatch.serverBlock().getProperties();
		writer.write("\t");
		writer.write(stateMismatch.block().location().toString());
		writer.write("\n");
		writer.write("\t\tClient-only Properties:\n");
		outputSidedProperties(writer, clientProperties, stateMismatch.clientOnlyProperties());
		writer.write("\n");
		writer.write("\t\tServer-only Properties:\n");
		outputSidedProperties(writer, serverProperties, stateMismatch.serverOnlyProperties());
		writer.write("\n");
		writer.write("\t\tMissing Property Values:\n");
		
		List<PropertyValueMismatch> propertyValueMismatches = stateMismatch.propertyValueMismatches();
		if(propertyValueMismatches.size() == 0) {
			writer.write("\t\t\tNone!\n");
		} else {
			for(final PropertyValueMismatch mismatch : propertyValueMismatches) {
				PropertyRepresentative clientProperty = clientProperties[mismatch.clientPropertyIndex];
				PropertyRepresentative serverProperty = serverProperties[mismatch.serverPropertyIndex];
				writer.write("\t\t\t");
				writer.write(clientProperty.name());
				if(clientProperty.propertyClass() != serverProperty.propertyClass()) {
					writer.write(" { client class = \"");
					writer.write(clientProperty.propertyClass());
					writer.write("\", server class = \"");
					writer.write(serverProperty.propertyClass());
					writer.write("\" }");
				}
				writer.write("\n");
				if(mismatch.clientOnlyValues.length > 0) {
					writer.write("\t\t\t\tClient-only values:\n");
					for(final int i : mismatch.clientOnlyValues) {
						writer.write("\t\t\t\t\t");
						writer.write(clientProperty.allowedValues()[i]);
						writer.write("\n");
					}
				}
				if(mismatch.serverOnlyValues.length > 0) {
					writer.write("\t\t\t\tServer-only values:\n");
					for(final int i : mismatch.serverOnlyValues) {
						writer.write("\t\t\t\t\t");
						writer.write(serverProperty.allowedValues()[i]);
						writer.write("\n");
					}
				}
			}
		}
	}
	
	private static void outputStateMismatches(FileWriter writer, List<StateMismatchResult> stateMismatches) throws IOException {
		writer.write("\n");
		writer.write("State mismatches:\n");
		if(stateMismatches.size() == 0) {
			writer.write("\tNone!\n");
		} else {
			for(StateMismatchResult stateMismatch : stateMismatches) {
				outputStateMismatch(writer, stateMismatch);
			}
		}
	}
	
	public static void outputMissingEntries(RegistryCompareResult compareResult, @Nullable List<StateMismatchResult> stateMismatches) {
		SynchronisedBlockstates.LOGGER.info("Writing missing states to file");
		final long startOutput = System.nanoTime();

		try {
			Path outputFolderPath = Files.createDirectories(SynchronisedBlockstatesClient.getOutputDirectory());
			String fileName = String.format("missing-states-%d.dat", System.currentTimeMillis());
			File outputFile = outputFolderPath.resolve(fileName).toFile();

			FileWriter writer = new FileWriter(outputFile);

			writer.write("Blocks present on the client missing from the server:\n");
			outputEntryList(writer, compareResult.clientOnlyBlocks());

			writer.write("\n");
			
			writer.write("Blocks present on the server missing from the client:\n");
			outputEntryList(writer, compareResult.serverOnlyBlocks());
			
			if(stateMismatches != null) {
				outputStateMismatches(writer, stateMismatches);
			}
			
			writer.close();
			
			final long endOutput = System.nanoTime();
			SynchronisedBlockstates.LOGGER.info("Writing missing states to file took {} milliseconds ({} nanos)", TimeUnit.MILLISECONDS.convert(endOutput - startOutput, TimeUnit.NANOSECONDS), endOutput - startOutput);
		} catch (IOException e) {
			SynchronisedBlockstates.LOGGER.error("Failed to output missing states to file", e);
		}
	}
	
	public static ClientAckResponse handleRegistryReceived(BlockInfoRegistry serverRegistry) {
		ClientNetworkHandler.markRegistryRecieved();
		
		SynchronisedBlockstates.LOGGER.info("Synchronised Blockstates is remapping the block registry");
		
		final long convertServerStartTime = System.nanoTime();
		BlockInfoRegistryCompareHelper serverCompareHelper = new BlockInfoRegistryCompareHelper(serverRegistry);
		final long convertServerEndTime = System.nanoTime();
		
		SynchronisedBlockstates.LOGGER.info("Synchronised Blockstates converted server registry in {} milliseconds ({} nanos)", TimeUnit.MILLISECONDS.convert(convertServerEndTime - convertServerStartTime, TimeUnit.NANOSECONDS), convertServerEndTime - convertServerStartTime);
		
		BlockInfoRegistry clientRegistry = MappingUtil.getOriginalBlockInfoRegistry();
		
		{
			BlockInfoRegistry currentClientRegistry = MappingUtil.getCurrentBlockInfoRegistry();
			if(currentClientRegistry != clientRegistry) {
				final long convertCurrentStartTime = System.nanoTime();
				BlockInfoRegistryCompareHelper currentCompareHelper = new BlockInfoRegistryCompareHelper(currentClientRegistry);
				final long convertCurrentEndTime = System.nanoTime();

				final long compareStartTime = System.nanoTime();
				RegistryCompareResult currentRegistryCompareResult = RegistryCompareResult.create(currentCompareHelper, serverCompareHelper);
				
				if(
						currentRegistryCompareResult.clientOnlyBlocks().size() == 0 && 
						currentRegistryCompareResult.serverOnlyBlocks().size() == 0 &&
						areStatesEqual(currentRegistryCompareResult.bothSidedBlocks())
					) {
					final long compareEndTime = System.nanoTime();

					SynchronisedBlockstates.LOGGER.info("Synchronised Blockstates converted current registry in {} milliseconds ({} nanos)", TimeUnit.MILLISECONDS.convert(convertCurrentEndTime - convertCurrentStartTime, TimeUnit.NANOSECONDS), convertCurrentEndTime - convertCurrentStartTime);
					SynchronisedBlockstates.LOGGER.info("Synchronised Blockstates compare current registry to server registry in {} milliseconds ({} nanos)", TimeUnit.MILLISECONDS.convert(compareEndTime - compareStartTime, TimeUnit.NANOSECONDS), compareEndTime - compareStartTime);
					SynchronisedBlockstates.LOGGER.info("Current block registry matches server block registry");
					
					return ClientAckResponse.OK;
				}
			}
		}
		

		final long convertClientStartTime = System.nanoTime();
		BlockInfoRegistryCompareHelper clientCompareHelper = new BlockInfoRegistryCompareHelper(clientRegistry);
		final long convertClientEndTime = System.nanoTime();

		SynchronisedBlockstates.LOGGER.info("Synchronised Blockstates converted original client registry in {} milliseconds ({} nanos)", TimeUnit.MILLISECONDS.convert(convertClientEndTime - convertClientStartTime, TimeUnit.NANOSECONDS), convertClientEndTime - convertClientStartTime);
		
		final long compareStartTime = System.nanoTime();
		RegistryCompareResult clientCompareResult = RegistryCompareResult.create(clientCompareHelper, serverCompareHelper);
		boolean isOriginalRegistryOkay = clientCompareResult.clientOnlyBlocks.size() == 0 && clientCompareResult.serverOnlyBlocks().size() == 0 && areStatesEqual(clientCompareResult.bothSidedBlocks());
		final long compareEndTime = System.nanoTime();
		
//		SynchronisedBlockstates.LOGGER.info("Client compare result: {} [blocks only on client = {}, blocks only on server = {}, blocks on both sides = {}]",
//				clientCompareResult, clientCompareResult.clientOnlyKeys().size(), clientCompareResult.serverOnlyKeys().size(), clientCompareResult.bothSidedBlocks().size());

		SynchronisedBlockstates.LOGGER.info("Synchronised Blockstates compared client registry to server registry in {} milliseconds ({} nanos)", TimeUnit.MILLISECONDS.convert(compareEndTime - compareStartTime, TimeUnit.NANOSECONDS), compareEndTime - compareStartTime);
		
		if(isOriginalRegistryOkay) {
			if(SynchronisedBlockstatesClient.shouldOutputMissingEntries()) {
				outputMissingEntries(clientCompareResult, null);
			}
			MappingUtil.restoreToOriginalBlockStateRegistry();
			SynchronisedBlockstates.LOGGER.info("Original client block registry is equal to server block registry...");
			return ClientAckResponse.OK;
		}

		SynchronisedBlockstates.LOGGER.info("Synchronised Blockstates remapping client registry to match server registry...");
		final long remapStartTime = System.nanoTime();
		RegistryRemapResult remapResult;
		try {
			remapResult = remapRegistry(clientCompareResult, clientRegistry, clientCompareHelper, serverRegistry, serverCompareHelper, SynchronisedBlockstatesClient.shouldOutputMissingEntries());
		} catch(Exception e) {
			e.printStackTrace();
			throw e;
		}
		
		MappingUtil.setCurrentMapper(remapResult.remappedRegistry());
		final long remapEndTime = System.nanoTime();
		SynchronisedBlockstates.LOGGER.info("Synchronised Blockstates remapped client registry to match server registry in {} milliseconds ({} nanos)", TimeUnit.MILLISECONDS.convert(remapEndTime - remapStartTime, TimeUnit.NANOSECONDS), remapEndTime - remapStartTime);

		if(SynchronisedBlockstatesClient.shouldOutputMissingEntries()) {
			outputMissingEntries(clientCompareResult, remapResult.missingStates());
		}
		
		return remapResult.ack();
	}
	
	public static boolean areStatesEqual(List<ClientServerBlockMatch> bothSidedBlocks) {

		// "Shallow" comparison to fail fast
		for(final ClientServerBlockMatch blockPair : bothSidedBlocks) {
			final var clientBlock = blockPair.clientBlockInfo();
			final var serverBlock = blockPair.serverBlockInfo();
			
			if(clientBlock.getStateCount() != serverBlock.getStateCount() || clientBlock.getPropertyCount() != serverBlock.getPropertyCount()) {
				return false;
			}
		}

		// "Deep" comparison to be thorough
		for(final ClientServerBlockMatch blockPair : bothSidedBlocks) {
			final var clientBlock = blockPair.clientBlockInfo();
			final var serverBlock = blockPair.serverBlockInfo();
			
			// at this point we have established:
			//     clientBlock.getStateCount() == serverBlock.getStateCount()
			//     clientBlock.getPropertyCount() == serverBlock.getPropertyCount()
			
			final int propertyCount = clientBlock.getPropertyCount();
			
			if(propertyCount == 0) { continue; }
			
			final ReferenceArraySet<PropertyRepresentative> clientProperties = new ReferenceArraySet<PropertyRepresentative>(clientBlock.getProperties().clone());
			
			final PropertyRepresentative[] serverProperties = serverBlock.getProperties();
			
			// check that all properties present on the server are the same on the client
			for(int i = 0; i < serverProperties.length; ++i) {
				if(!clientProperties.remove(serverProperties[i])) {
					return false;
				}
			}
			
			// since we've established that the number of properties are the same on both the client and server,
			//     it shouldn't be possible for properties to exist on the client that don't exist on the server
			//     because all the properties that exist on the server exist on the client (unless the implementation
			//     has already been changed so much that we couldn't trust it to give correct results with more tests)
		}
		
		return true;
	}
	
	public static record RegistryRemapResult(RemappingIdMapper<BlockState> remappedRegistry, ClientAckResponse ack, boolean outputMissingStates, @Nullable List<StateMismatchResult> missingStates) {
		
	}
	
	public static record PropertyValueMismatch(int clientPropertyIndex, int serverPropertyIndex, int[] clientOnlyValues, int[] serverOnlyValues) {}
	
	public static record StateMismatchResult(ResourceKey<Block> block, RegistryBlockInfoWrapper.Impl clientBlock, RegistryBlockInfoWrapper.Impl serverBlock, int[] clientOnlyProperties, int[] serverOnlyProperties, List<PropertyValueMismatch> propertyValueMismatches) {
		
	}
	
	@FunctionalInterface
	public static interface MappingSetter {
		public void setMapping(int clientIndex, int serverMapping);
	}
	
	/**
	 * Creates an array containing the values {0, 1, ..., maxExclusive - 2, maxExclusive - 1}
	 * @param maxExclusive the number of elements
	 * @return
	 */
	public static int[] intRange(final int maxExclusive) {
		final int[] array = new int[maxExclusive];
		for(int i = maxExclusive; i-- != 0;) {array[i] = i;}
		return array;
	}

//	/**
//	 * Returns an array containing the values {minInclusive, minInclusive + 1, ..., maxExclusive - 1}
//	 * @param minInclusive
//	 * @param maxExclusive
//	 * @return
//	 */
//	public static int[] intRange(final int minInclusive, final int maxExclusive) {
//		final int count = maxExclusive - minInclusive;
//		int[] a = new int[count];
//		for(int i = 0; i < count; ++i) {
//			a[i] = i + minInclusive;
//		}
//		return a;
//	}
	
	/**
	 * Returns an array containing the values {0, 1 * scale, 2 * scale, ..., (maxExclusive - 1) * scale}
	 * @param maxExclusive the number of elements
	 * @param scale the multiplied scale of the elements
	 * @return
	 */
	public static int[] intRangeScaled(final int maxExclusive, final int scale) {
		final int[] array = new int[maxExclusive];
		for(int i = maxExclusive; i-- != 0;) {array[i] = i * scale;}
		return array;
	}
	
	/**
	 * serverIndexToClientValueMap: maps every server-side allowed value index -> client-side allowed value index (scaled by a multiplier, such that it can be added to a property value)
	 * clientOnlyValueIndexes: property indexes for values that only exist on the client
	 * serverOnlyValueIndexes: property indexes for values that only exist on the server
	 */
	public static record PropertySetDifference(int[] serverIndexToClientValueMap, int[] clientOnlyValueIndexes, int[] serverOnlyValueIndexes) {}
	
	/**
	 * Returns the int property index mapping from each value of a serverProperty to its clientProperty counterpart
	 * @param serverProperty
	 * @param clientProperty
	 * @param clientPropertyMultiplier
	 * @return
	 */
	public static PropertySetDifference allowedPropertyValueSetIntersectionMap(PropertyRepresentative serverProperty, PropertyRepresentative clientProperty, int clientPropertyMultiplier) {
		final String[] serverAllowedValues = serverProperty.allowedValues();
		final String[] clientAllowedValues = clientProperty.allowedValues();

		final int serverValueCount = serverAllowedValues.length;
		
//		SynchronisedBlockstates.LOGGER.info("Compare mapping {} (values {}) and {} (values {}), generating map with size {}", serverProperty, Arrays.toString(serverProperty.allowedValues()), clientProperty, Arrays.toString(clientProperty.allowedValues()), serverValueCount);
		int serverOnlyValueCount = 0;
		final int[] serverOnlyValueIndexes = new int[serverValueCount];
		
		final int[] serverIndexToClientValueMap = new int[serverValueCount];
		
		Reference2IntArrayMap<String> clientValueMap = new Reference2IntArrayMap<String>(clientAllowedValues.clone(), intRange(clientAllowedValues.length));
		clientValueMap.defaultReturnValue(-1);
		
		for(int serverIndex = 0; serverIndex < serverValueCount; ++serverIndex) {
			final int clientIndex = clientValueMap.removeInt(serverAllowedValues[serverIndex]); // -1 if doesn't exist
			if(clientIndex < 0) {
				serverIndexToClientValueMap[serverIndex] = 0;
				serverOnlyValueIndexes[serverOnlyValueCount++] = serverIndex;
			} else {
				serverIndexToClientValueMap[serverIndex] = clientIndex * clientPropertyMultiplier;
			}
		}
		
		// clientValueMap.values().toIntArray()

//		SynchronisedBlockstates.LOGGER.info("Generating mappings {}", Arrays.toString(serverIndexToClientValueMap));
//		clientValueMap.values().toIntArray();
		return new PropertySetDifference(
				serverIndexToClientValueMap,
				clientValueMap.values().toIntArray(),
				Arrays.copyOf(serverOnlyValueIndexes, serverOnlyValueCount)
			);
	}
	
	public static RegistryRemapResult remapRegistry(RegistryCompareResult compareResult, BlockInfoRegistry clientRegistry, BlockInfoRegistryCompareHelper clientRegistryHelper, BlockInfoRegistry serverRegistry, BlockInfoRegistryCompareHelper serverRegistryHelper, final boolean outputMissingStates) {

		final IdMapper<BlockState> originalMapper = MappingUtil.getOriginalBlockStateRegistry();
		
		final int AIR_ID = originalMapper.getId(Blocks.VOID_AIR.defaultBlockState());
		
		RemappingIdMapper<BlockState> remappedRegistry = new RemappingIdMapper<BlockState>(serverRegistryHelper.stateCount, originalMapper, RemappingIdMapper.NO_VALUE_INDEX, false);

		// mapping setter helper
		MappingSetter mappingSetter = (clientIndex, serverMapping) -> {
			// add the numerical mapping
			remappedRegistry.setMapping(clientIndex, serverMapping);
			// add the BlockState mapping
			if(serverMapping != RemappingIdMapper.NO_VALUE_INDEX)
				remappedRegistry.addMappingRaw(originalMapper.byId(clientIndex), serverMapping);
		};
		
		// remap all client-only blocks to "i have no mapping"
		for(final var clientOnlyBlock : compareResult.clientOnlyBlocks()) {
			for(final int index : clientOnlyBlock.getStateGlobalIndexes()) {
				mappingSetter.setMapping(index, RemappingIdMapper.NO_VALUE_INDEX);
			}
		}

		// remap all server-only blocks to air
		for(final var serverOnlyBlock : compareResult.serverOnlyBlocks()) {
			for(final int index : serverOnlyBlock.getStateGlobalIndexes()) {
				mappingSetter.setMapping(AIR_ID, index);
			}
		}
		
		List<StateMismatchResult> mismatchList = outputMissingStates ? new ArrayList<>(compareResult.bothSidedBlocks().size()) : null;
		
		// remap all blocks that appear on both sides
		for(final ClientServerBlockMatch dualSidedBlock : compareResult.bothSidedBlocks()) {
			// "babe PLEASE don't make it a single massive overly complex function that'll be impossible to understand or edit later!"
			// the 5 nanoseconds i think i can save (my pre-emptive optimizations will inevitably cost more than i save):
			
			RegistryBlockInfoWrapper.Impl clientBlock = dualSidedBlock.clientBlockInfo();
			RegistryBlockInfoWrapper.Impl serverBlock = dualSidedBlock.serverBlockInfo();

			final int clientPropertyCount = clientBlock.getPropertyCount();
			final int serverPropertyCount = serverBlock.getPropertyCount();

			final int[] clientStateIds = clientBlock.getStateGlobalIndexes();
			final int[] serverStateIds = serverBlock.getStateGlobalIndexes();

			// If there are no properties on either side, trivial mapping
			if(clientPropertyCount == 0 && serverPropertyCount == 0) {
				if(clientStateIds.length != 0 && serverStateIds.length != 0)
					mappingSetter.setMapping(clientStateIds[0], serverStateIds[0]);
				continue;
			}
			
			final PropertyRepresentative[] clientProperties = clientBlock.getProperties();
			final PropertyRepresentative[] serverProperties = serverBlock.getProperties();

			// if the properties are somehow entirely equal, skip all this stuff and immediately copy the mappings
			if(Arrays.equals(clientProperties, serverProperties)) {
				final int length = Math.min(clientStateIds.length, serverStateIds.length);
				for(int i = 0; i < length; ++i) {
					mappingSetter.setMapping(clientStateIds[i], serverStateIds[i]);
				}
				continue;
			}

			// ======== DEBUG CODE ========
//			for(final int index : serverStateIds) {
//				mappingSetter.setMapping(AIR_ID, index);
//			}
//			for(final int index : clientStateIds) {
//				mappingSetter.setMapping(index, RemappingIdMapper.NO_VALUE_INDEX);
//			}
			// ============================
			
			// TODO it might be faster to instead work from the client to the server ids because we'll be more likely to be setting adjacent values in the mappings
			
			// ======== Generate some information now that we'll need to use later ========
			
			// if server property [0] has 2 values and [1] has 6, then this would be {2, 6, ...}
			final int[] serverPropertySizes = new int[serverPropertyCount];

			// how much to multiply a value by to make it line up with the client property value
			// using {2, 6, 5, ...} as an example: {1, 2, 12, 60, ...}
			final int[] clientPropertySizes = new int[clientPropertyCount];
			final int[] clientPropertyMultipliers = new int[clientPropertyCount];

			final int[][] serverToClientPropertyValueIndexMap = new int[serverPropertyCount][];
			
			{
				for(int i = serverPropertyCount; i-- != 0;) {
					serverPropertySizes[i] = serverProperties[i].allowedValues().length;
				}
				
				if(clientPropertyCount != 0) {
					int clientMultiplier = 1;
					for(int i = clientPropertyCount; i-- != 0;) {
						clientPropertyMultipliers[i] = clientMultiplier;
						clientMultiplier *= (clientPropertySizes[i] = clientProperties[i].allowedValues().length);
					}
				}
			}
			
			// ============================================================================
			
			
			// ======== Figure out which properties exist only on the server/client ========
			final Reference2IntArrayMap<PropertyRepresentative> clientOnlyProperties = new Reference2IntArrayMap<PropertyRepresentative>(clientProperties.clone(), intRange(clientPropertyCount));
			clientOnlyProperties.defaultReturnValue(-1);
			final IntArrayList serverOnlyProperties = new IntArrayList(serverPropertyCount);

			// server property index --> client property index
			int[] serverToClientPropertyMap = new int[serverPropertyCount];
			Arrays.fill(serverToClientPropertyMap, -1);
			
			for(int serverIndex = 0; serverIndex < serverProperties.length; ++serverIndex) {
				final int clientIndex = clientOnlyProperties.removeInt(serverProperties[serverIndex]);
				if(clientIndex >= 0) { // comparisons with zero are faster than != -1
					serverToClientPropertyMap[serverIndex] = clientIndex;
					serverToClientPropertyValueIndexMap[serverIndex] = intRangeScaled(serverPropertySizes[serverIndex], clientPropertyMultipliers[clientIndex]);
				} else {
					serverOnlyProperties.add(serverIndex);
				}
			}
			
			// =============================================================================

			final int[][] propertyValueMismatchMap = new int[clientPropertyCount][];
			final int[] propertyValueMismatchMapClientPropertyIndexToServerPropertyIndex = new int[clientPropertyCount]; // look eventually i just gave up on naming stuff
			final ObjectArrayList<PropertyValueMismatch> propertyValueMismatch = outputMissingStates ? new ObjectArrayList<>(Math.max(clientPropertyCount, serverPropertyCount)) : null;
			
			// ======== Handle server/client differences for remapped class names and unique property values ========
			boolean hasClientSidedPropertyValues = false;
			if(clientOnlyProperties.size() != 0 && serverOnlyProperties.size() != 0) {
				final var clientPropertyEntrySet = clientOnlyProperties.reference2IntEntrySet();
				int serverOnlyCount = serverOnlyProperties.size();
				int i = 0;
				serverPropertyLoop: while(i < serverOnlyCount) {
					final int serverIndex = serverOnlyProperties.getInt(i);
					final PropertyRepresentative serverProperty = serverProperties[serverIndex];
					
					final var clientPropertyIterator = clientPropertyEntrySet.fastIterator();
					while(clientPropertyIterator.hasNext()) {
						final var entry = clientPropertyIterator.next();
						final PropertyRepresentative clientProperty = entry.getKey();
						if(clientProperty.name() == serverProperty.name()) {
							final int clientIndex = entry.getIntValue();

							// Map the property indexes
							serverToClientPropertyMap[serverIndex] = clientIndex;
							
							// Map any possibly mismatched property values
							PropertySetDifference propertySetDifference = allowedPropertyValueSetIntersectionMap(serverProperty, clientProperty, clientPropertyMultipliers[clientIndex]);
							serverToClientPropertyValueIndexMap[serverIndex] = propertySetDifference.serverIndexToClientValueMap();
							final int[] serverOnlyValueIndexes = propertySetDifference.serverOnlyValueIndexes();
							final int[] clientOnlyValueIndexes = propertySetDifference.clientOnlyValueIndexes();
							final boolean hasClientOnlyValues = clientOnlyValueIndexes.length != 0;
							if(hasClientOnlyValues) { // we only care about client-only values here, server-only values all get mapped to 0
								hasClientSidedPropertyValues = true;
								propertyValueMismatchMap[clientIndex] = clientOnlyValueIndexes;
								propertyValueMismatchMapClientPropertyIndexToServerPropertyIndex[clientIndex] = serverIndex;
							}
							if(outputMissingStates && (serverOnlyValueIndexes.length != 0 || hasClientOnlyValues)) {
								propertyValueMismatch.add(new PropertyValueMismatch(clientIndex, serverIndex, clientOnlyValueIndexes, serverOnlyValueIndexes));
							}
							
							// remove the property from the client-only properties list
							clientPropertyIterator.remove();
							// remove the property from the server-only properties list
							int lastElement = serverOnlyProperties.popInt();
							if(i != --serverOnlyCount) {
								serverOnlyProperties.set(i, lastElement);
							}
							
							// continue so we:
							// 1. don't keep iterating (we don't need to)
							// 2. don't increment i (there is now a new element in i)
							continue serverPropertyLoop;
						}
					}
					
					i++;
				}
			}

			// ======================================================================================================

			// ======== Do handling for server-only properties ========
			// Map all server-only properties to 0 (as if they weren't present at all)
			// Server-only property values are handled by the call to allowedPropertyValueSetIntersectionMap() - they get mapped to the default value for that property
			if(serverOnlyProperties.size() != 0) {
				for(int i = 0; i < serverOnlyProperties.size(); ++i) {
					int serverPropertyIndex = serverOnlyProperties.getInt(i);
					serverToClientPropertyValueIndexMap[serverPropertyIndex] = new int[serverPropertySizes[serverPropertyIndex]];
				}
			}
			// ========================================================

			if(outputMissingStates && (clientOnlyProperties.size() != 0 || serverOnlyProperties.size() != 0 || propertyValueMismatch.size() != 0)) {
				mismatchList.add(new StateMismatchResult(clientBlock.getKey(), clientBlock, serverBlock, clientOnlyProperties.values().toIntArray(), serverOnlyProperties.toIntArray(), propertyValueMismatch));
			}
			
			// ======== Client-only and server-only properties can all be matched up ========
			if(clientOnlyProperties.size() == 0 && !hasClientSidedPropertyValues) {
				final int stateCount = serverStateIds.length;
				
				int[] serverStateIndexesToClientStateIndexes = new int[stateCount];
				
				int incrementScale = 1;
				for(int serverIndex = serverPropertyCount; serverIndex-- != 0;) {
					final int propertySize = serverPropertySizes[serverIndex];
					final int[] propertyValues = serverToClientPropertyValueIndexMap[propertySize];
					
					int propertyIndex = 0;
					for(int stateIndex = 0; stateIndex <= stateCount; ++stateIndex) {
						serverStateIndexesToClientStateIndexes[stateIndex] += propertyValues[propertyIndex];
						if(stateIndex % incrementScale == 0) {
							propertyIndex = ++propertyIndex % propertySize;
						}
					}
					
					incrementScale *= propertySize;
				}
				
				for(int serverStateIndex = serverPropertyCount; serverStateIndex-- != 0;) {
					final int serverStateId = serverStateIds[serverStateIndex];
					final int clientStateIndex = serverStateIndexesToClientStateIndexes[serverStateIndex];
					final int clientStateId = clientStateIds[clientStateIndex];
					
					mappingSetter.setMapping(clientStateId, serverStateId);
				}
				
				continue;
			}
			// =====================================================================
			
			// Do detection & handling for entirely unique (single-sided) properties
			
			// If there are client-only properties...
			int[] clientOnlyPropertyValueIds = new int[] {0};
			if(clientOnlyProperties.size() != 0) {
				final int[] clientOnlyPropertyIndexes = clientOnlyProperties.values().toIntArray();
				final int clientOnlyPropertyCount = clientOnlyPropertyIndexes.length;
				int clientOnlyPropertyValueCombinations = 1;
				
				for(int clientIndex : clientOnlyPropertyIndexes) {
					final int size = clientPropertySizes[clientIndex];
					clientOnlyPropertyValueCombinations *= size;
				}
				
				clientOnlyPropertyValueIds = new int[clientOnlyPropertyValueCombinations];
				
				int incrementScale = 1;
				for(int i = clientOnlyPropertyCount; i-- != 0;) {
					final int clientIndex = clientOnlyPropertyIndexes[i];
					final int propertyMultiplier = clientPropertyMultipliers[clientIndex];
					final int propertySize = clientPropertySizes[clientIndex];
					
					int propertyIndex = 0;
					for(int mapIndex = 0; mapIndex <= clientOnlyPropertyValueCombinations; ++mapIndex) {
						// i believe this to be faster than the map method because caching and no intRangeScaled function call and less instructions
						clientOnlyPropertyValueIds[mapIndex] += propertyIndex * propertyMultiplier;
						if(mapIndex % incrementScale == 0) {
							propertyIndex = ++propertyIndex % propertySize;
						}
					}
					
					incrementScale *= propertySize;
				}
			}

			// handling for client-only property values (property exists on both sides, but one or more allowed values for that property exist only on the client)
			/*
			 * Maps [client property index] -> an array of values that contain the index of client-only property values for that property.
			 * For example, if you have:
			 * 1. On the client: 
			 *     property abc[index = 5, values = {0: "xyz", 1: "foo", 2: "bar", 3: "baz"}]
			 *     property def[index = 2, values = {0: "false", 1: "true"}]
			 * 2. On the server:
			 *     property abc[index = 6, values = {0: "foo", 1: "bar"}]
			 *     property def[index = 2, values = {0: "false", 1: "true"}]
			 * Then this would contain the mappings:
			 * {
			 *     [5]: [0 * <multiplier of abc>, 3 * <multiplier of abc>]
			 * }
			 * because:
			 * 1. abc has property index 5 on the client
			 *     1. property value "xyz" has index 0 on the client
			 *     2. property value "baz" has index 3 on the client
			 * // 1.1 is technically a lie I think, disregard that
			 * // TODO do an example about when a server-only property with server index 0 references a client-only property and how that doesn't get a mapping because server-only properties always map to 0
			 * 
			 * The mapper will look at this when it maps *server index 0* (which would be "foo") and then also map the property with those same values to that same index
			 * 
			 * Using <-> to show mappings:
			 *     block[abc="foo", def="false"] <-> block[abc="foo", def="false"], block[abc="xyz", def="false"], block[abc="baz", def="false"]
			 *     block[abc="bar", def="false"] <-> block[abc="bar", def="false"]
			 *     block[abc="foo", def="true"] <-> block[abc="foo", def="true"], block[abc="xyz", def="true"], block[abc="baz", def="true"]
			 *     block[abc="bar", def="true"] <-> block[abc="bar", def="true"]
			 */
			int[][] clientPropertyDefaultValueToClientOnlyPropertyValueMapping = new int[clientPropertyCount][];
			if(hasClientSidedPropertyValues) {
				for(int clientPropertyIndex = clientPropertyCount; clientPropertyIndex-- != 0;) {
					final int[] clientOnlyValueIndexes = propertyValueMismatchMap[clientPropertyIndex];
					if(clientOnlyValueIndexes == null) {
						clientPropertyDefaultValueToClientOnlyPropertyValueMapping[clientPropertyIndex] = new int[0];
						continue;
					}
					int clientOnlyValueCount = clientOnlyValueIndexes.length;
					final int multiplier = clientPropertyMultipliers[clientPropertyIndex];
					final int serverPropertyIndex = propertyValueMismatchMapClientPropertyIndexToServerPropertyIndex[clientPropertyIndex];
					final int defaultClientScaledValue = serverToClientPropertyValueIndexMap[serverPropertyIndex][0];

					if(defaultClientScaledValue == 0) {
						// only chance for server value 0 to point to a client-only value is if the server value is a server-only value and points to 0 and client value 0 is client-only
						for(int i = clientOnlyValueCount; i-- != 0;) {
							if(clientOnlyValueIndexes[i] == 0) {
								// swap it so it's last and then reduce the count to remove duplicate values
								IntArrays.swap(clientOnlyValueIndexes, i, --clientOnlyValueCount);
								break;
							}
						}
					}
					
					final int[] clientOnlyPropertyValueMapping = new int[clientOnlyValueCount];
					
					for(int i = clientOnlyValueCount; i-- != 0;) {
						clientOnlyPropertyValueMapping[i] = clientOnlyValueIndexes[i] * multiplier;
					}

					clientPropertyDefaultValueToClientOnlyPropertyValueMapping[clientPropertyIndex] = clientOnlyPropertyValueMapping;
				}
			}
			
			if(clientOnlyProperties.size() != 0 || serverOnlyProperties.size() != 0) {
				SynchronisedBlockstates.LOGGER.info("Block {} has the following incompatible properties: {} (client-only) and {} (server-only)", dualSidedBlock.resourceKey(), clientOnlyProperties, serverOnlyProperties);
			}
			
			// ======== DEBUG CODE ========
//			for(final int index : clientStateIds) {
//				mappingSetter.setMapping(index, serverStateIds[0]);
//			}
//			for(final int index : serverStateIds) {
////			for(int i = 1; i < serverStateIds.length; ++i) {
////				final int index = serverStateIds[i];
//				mappingSetter.setMapping(clientStateIds[0], index);
//			}
			// ============================

			// ======== DEBUG CODE ========
//			// If there are now no property mismatches, we can now map between the two pretty trivially
//			if(clientOnlyProperties.size() == 0 && serverOnlyProperties.size() == 0) {
			// ============================
			

			// ======== DEBUG CODE ========
//			final int[] clientPropertyMultipliersMapped = new int[propertyCount];
//			for(int serverIndex = 0; serverIndex < propertyCount; ++serverIndex) {
//				clientPropertyMultipliersMapped[serverIndex] = clientPropertyMultipliers[serverToClientPropertyMap[serverIndex]];
//			}
			// ============================
			
			final int[] clientStateIndexes = new int[clientStateIds.length];
			int clientStateIndexCount = 0;
			for(int stateIndex = serverStateIds.length; stateIndex-- != 0;) {
				final int serverStateId = serverStateIds[stateIndex];

				clientStateIndexCount = 1;
				
				int serverStateIndex = stateIndex;
				clientStateIndexes[0] = 0;
				
				for(int serverPropertyIndex = serverPropertyCount; serverPropertyIndex-- != 0;) {
					final int allowedValueCount = serverPropertySizes[serverPropertyIndex];
					final int serverPropertyValueIndex = serverStateIndex % allowedValueCount;
					serverStateIndex /= allowedValueCount;

					// ======== DEBUG CODE ========
//					final int clientPropertyIndex = serverToClientPropertyMap[serverPropertyIndex];
//					
//					final int clientMapping = serverToClientPropertyValueIndexMap[serverPropertyIndex][serverPropertyValueIndex];
//					
//					SynchronisedBlockstates.LOGGER.info(
//							"Server Property {}={} (index {}) maps to client Property {}={} (index {})", 
//							serverProperties[serverPropertyIndex].name(), serverProperties[serverPropertyIndex].allowedValues()[serverPropertyValueIndex], serverPropertyValueIndex,
//							clientProperties[clientPropertyIndex].name(), clientProperties[clientPropertyIndex].allowedValues()[clientMapping], clientMapping);
//
//					clientStateIndex += clientMapping * clientPropertyMultipliers[clientPropertyIndex];
					// ============================

					final int size = clientStateIndexCount;
					final int value = serverToClientPropertyValueIndexMap[serverPropertyIndex][serverPropertyValueIndex];
					
					final int[] allClientMappings;
					if(serverPropertyValueIndex == 0 && (allClientMappings = clientPropertyDefaultValueToClientOnlyPropertyValueMapping[serverToClientPropertyMap[serverPropertyIndex]]) != null) {
						for(final int clientMapping : allClientMappings) {
							for(int i = size; i-- != 0;) {
								clientStateIndexes[clientStateIndexCount++] = clientStateIndexes[i] + clientMapping;
							}
						}
					}
					
					for(int i = size; i-- != 0;) {
						clientStateIndexes[i] += value;
					}
				}

				for(int i = clientOnlyPropertyValueIds.length; i-- != 0;) {
					for(int j = clientStateIndexCount; j-- != 0;) {
						final int clientStateIndex = clientStateIndexes[j];
						final int clientStateId = clientStateIds[clientStateIndex + clientOnlyPropertyValueIds[i]];

						// ======== DEBUG CODE ========
//						{
//							SynchronisedBlockstates.LOGGER.info("Mapped {} to {}", stateIndex, clientStateIndex);
//							SynchronisedBlockstates.LOGGER.info("Unwrapping {} (id {})", clientStateIndex);
//							
//							int index = clientStateIndex;
//							for(int clientProperty = clientPropertyCount; clientProperty-- != 0;) {
//								final PropertyRepresentative property = clientProperties[clientProperty];
//								final int currentValue = index % property.allowedValues().length;
//								index /= property.allowedValues().length;
//		
//								SynchronisedBlockstates.LOGGER.info(
//										"Unwrapped property {} {}={} (index {})", clientProperty,
//										property.name(), property.allowedValues()[currentValue], currentValue
//								);
//							}
//							
//							SynchronisedBlockstates.LOGGER.info("Unwrapped Blockstate is {}", originalMapper.byId(clientStateId));
//						}
						// ============================
						
						mappingSetter.setMapping(clientStateId, serverStateId);
					}
				}
			}
		}
		
		// ensure the air block retains its proper mapping
		Blocks.VOID_AIR.defaultBlockState().getBlockHolder().unwrapKey().ifPresent(t -> {
			mappingSetter.setMapping(AIR_ID, serverRegistryHelper.blockInfoMap.get(t).getStateGlobalIndexes()[0]);
		});
		
		return new RegistryRemapResult(remappedRegistry, ClientAckResponse.OK, outputMissingStates, mismatchList);
	}
}
