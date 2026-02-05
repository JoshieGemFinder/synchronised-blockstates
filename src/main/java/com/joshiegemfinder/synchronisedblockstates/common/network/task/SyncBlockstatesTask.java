package com.joshiegemfinder.synchronisedblockstates.common.network.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.network.login.QueryIdGenerator;
import com.joshiegemfinder.synchronisedblockstates.common.network.login.Task;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryBlockInfoPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryCompletePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryPropertyClassTablePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryPropertyPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryStartPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryStringTablePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.LoginTaskProbePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.UnchunkedBlockRegistryPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.util.NetworkedProperty;
import com.joshiegemfinder.synchronisedblockstates.common.network.util.NetworkedPropertyRegistry;
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry;
import com.joshiegemfinder.synchronisedblockstates.common.util.RegistryBlockInfoWrapper;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public class SyncBlockstatesTask implements Task {
	public static final Task.Type<SyncBlockstatesTask> TYPE = new Task.Type<SyncBlockstatesTask>(new ResourceLocation(SynchronisedBlockstates.MOD_ID, "sync_blockstates_task"));

	// Measured in bytes
	public static final int PROPERTY_CLASS_TABLE_CHUNKING_THRESHOLD_BYTES = Integer.getInteger("mod.synchronisedblockstates.propertyClassChunkingThresholdBytes", 16384);
	public static final int PROPERTY_STRING_TABLE_CHUNKING_THRESHOLD_BYTES = Integer.getInteger("mod.synchronisedblockstates.propertyStringTableChunkingThresholdBytes", 16384);
	public static final int PROPERTY_CHUNKING_THRESHOLD_BYTES = Integer.getInteger("mod.synchronisedblockstates.propertyChunkingThresholdBytes", 16384);
	
	// Not measured in bytes
	public static final int BLOCK_CHUNKING_THRESHOLD = Integer.getInteger("mod.synchronisedblockstates.blockChunkingThreshold", 4096);
	public static final int STATE_CHUNKING_THRESHOLD = Integer.getInteger("mod.synchronisedblockstates.stateChunkingThreshold", 32768);
	
	protected final QueryIdGenerator queryIdGenerator;
	
	public SyncBlockstatesTask(QueryIdGenerator queryIdGenerator) {
		this.queryIdGenerator = queryIdGenerator;
	}
	
	public <T> ClientboundCustomQueryPacket createPacket(ResourceLocation type, T packet, BiConsumer<FriendlyByteBuf, T> encoder) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		encoder.accept(buf, packet);
		return new ClientboundCustomQueryPacket(this.queryIdGenerator.nextQueryId(), type, buf);
	}
	
	@Override
	public void start(Consumer<Packet<?>> consumer) {
		SynchronisedBlockstates.LOGGER.info("Starting blockstate sync task");
		
		// probe them first to make sure they'll even be able to accept our registry
		// no point sending such a large amount of data if they'll just discard it
		LoginTaskProbePacket probePacket = new LoginTaskProbePacket();
		
		consumer.accept(createPacket(LoginTaskProbePacket.TYPE, probePacket, LoginTaskProbePacket::encode));
	}

	public void sendRegistry(Consumer<Packet<?>> consumer) {
//		SynchronisedBlockstates.LOGGER.info("Wow! Both the server and client both have the same network version! That's cool! I'll time out now!");
		
		BlockInfoRegistry registry = BlockInfoRegistry.createRegistry(Block.BLOCK_STATE_REGISTRY);
		
		boolean shouldChunkData = registry.getProperties().length > PROPERTY_CHUNKING_THRESHOLD_BYTES
								|| registry.getBlocks().length > BLOCK_CHUNKING_THRESHOLD
								|| registry.getStateCount() > STATE_CHUNKING_THRESHOLD;
		
		if(shouldChunkData) {
			sendChunkedRegistry(consumer, registry);
		} else {
			sendUnchunkedRegistry(consumer, registry);
		}
	}
	
	public void sendUnchunkedRegistry(Consumer<Packet<?>> consumer, BlockInfoRegistry registry) {
		UnchunkedBlockRegistryPacket packet = new UnchunkedBlockRegistryPacket(registry);
		
		consumer.accept(createPacket(UnchunkedBlockRegistryPacket.TYPE, packet, UnchunkedBlockRegistryPacket::encode));
	}

	protected final AtomicReference<UUID> chunkedRegistryUUID = new AtomicReference<UUID>();
	protected final AtomicReference<BlockInfoRegistry> chunkedRegistryCache = new AtomicReference<BlockInfoRegistry>();
	protected final AtomicInteger chunkedDataPacketsRemaining = new AtomicInteger(0);
	
	public void sendChunkedRegistry(Consumer<Packet<?>> consumer, BlockInfoRegistry registry) {
		/*
		 * Send start packet and wait for response to ensure:
		 * 1.	The client will actually be able to process this data (there shouldn't be another decoding happening with the same UUID, but it's technically not impossible)
		 * 2.	The client doesn't receive any data packets before the start packet (so it can actually put this somewhere)
		 */
		
		this.chunkedRegistryUUID.set(UUID.randomUUID());
		this.chunkedRegistryCache.set(registry);
		final NetworkedPropertyRegistry networkedRegistry = registry.getNetworkedPropertyRegistry();
		
		ChunkedBlockRegistryStartPacket startPacket = new ChunkedBlockRegistryStartPacket(this.chunkedRegistryUUID.get(),
				networkedRegistry.getClassTableSize(), networkedRegistry.getStringTableSize(),
				registry.getProperties().length,
				registry.getBlocks().length
			);
		
		consumer.accept(createPacket(ChunkedBlockRegistryStartPacket.TYPE, startPacket, ChunkedBlockRegistryStartPacket::encode));
	}
	
	// TODO maybe merge these three methods into a single method that can be reused
	
	protected void sendPropertyClassesTable(Consumer<Packet<?>> consumer, UUID uuid, NetworkedPropertyRegistry networkedRegistry) {
		final int maxSizeBytes = PROPERTY_CLASS_TABLE_CHUNKING_THRESHOLD_BYTES;
		final String[] propertyClassTable = networkedRegistry.getRemappedPropertyClasses();
		final int propertyClassTableSize = propertyClassTable.length;

		int chunkSizeBytes = 0;
		int chunkStartIndex = 0;
		
		for(int i = 0; i < propertyClassTableSize; ++i) {
			String data = propertyClassTable[i];
			int dataSize = 2 + data.length();
			if(chunkSizeBytes + dataSize > maxSizeBytes) {
				// Copy a section of the table with range [chunkStartIndex, i); that is, chunkStartIndex inclusive, i exclusive
				String[] chunk = Arrays.copyOfRange(propertyClassTable, chunkStartIndex, i);
				
				// Create & send the packet
				ChunkedBlockRegistryPropertyClassTablePacket chunkPacket = new ChunkedBlockRegistryPropertyClassTablePacket(uuid, chunkStartIndex, chunk);
				consumer.accept(createPacket(ChunkedBlockRegistryPropertyClassTablePacket.TYPE, chunkPacket, ChunkedBlockRegistryPropertyClassTablePacket::encode));
				
				// Start a new chunk at this index
				chunkSizeBytes = 0;
				chunkStartIndex = i;
			}
			
			chunkSizeBytes += dataSize;
		}
		
		// Send the final chunk
		
		// Get the final section of the array
		String[] chunk = Arrays.copyOfRange(propertyClassTable, chunkStartIndex, propertyClassTableSize);

		// Create & send the final packet
		ChunkedBlockRegistryPropertyClassTablePacket chunkPacket = new ChunkedBlockRegistryPropertyClassTablePacket(uuid, chunkStartIndex, chunk);
		consumer.accept(createPacket(ChunkedBlockRegistryPropertyClassTablePacket.TYPE, chunkPacket, ChunkedBlockRegistryPropertyClassTablePacket::encode));
	}

	protected void sendPropertyStringTable(Consumer<Packet<?>> consumer, UUID uuid, NetworkedPropertyRegistry networkedRegistry) {
		final int maxSizeBytes = PROPERTY_STRING_TABLE_CHUNKING_THRESHOLD_BYTES;
		final String[] stringTable = networkedRegistry.getStringTable();
		final int stringTableSize = stringTable.length;

		int chunkSizeBytes = 0;
		int chunkStartIndex = 0;
		
		for(int i = 0; i < stringTableSize; ++i) {
			String data = stringTable[i];
			int dataSize = 2 + data.length();
			if(chunkSizeBytes + dataSize > maxSizeBytes) {
				// Copy a section of the table with range [chunkStartIndex, i); that is, chunkStartIndex inclusive, i exclusive
				String[] chunk = Arrays.copyOfRange(stringTable, chunkStartIndex, i);
				
				// Create & send the packet
				ChunkedBlockRegistryStringTablePacket chunkPacket = new ChunkedBlockRegistryStringTablePacket(uuid, chunkStartIndex, chunk);
				consumer.accept(createPacket(ChunkedBlockRegistryStringTablePacket.TYPE, chunkPacket, ChunkedBlockRegistryStringTablePacket::encode));
				
				// Start a new chunk at this index
				chunkSizeBytes = 0;
				chunkStartIndex = i;
			}
			
			chunkSizeBytes += dataSize;
		}
		
		// Send the final chunk
		
		// Get the final section of the array
		String[] chunk = Arrays.copyOfRange(stringTable, chunkStartIndex, stringTableSize);

		// Create & send the final packet
		ChunkedBlockRegistryStringTablePacket chunkPacket = new ChunkedBlockRegistryStringTablePacket(uuid, chunkStartIndex, chunk);
		consumer.accept(createPacket(ChunkedBlockRegistryStringTablePacket.TYPE, chunkPacket, ChunkedBlockRegistryStringTablePacket::encode));
	}

	protected void sendPropertyTable(Consumer<Packet<?>> consumer, UUID uuid, NetworkedPropertyRegistry networkedRegistry) {
		final int maxSizeBytes = PROPERTY_CHUNKING_THRESHOLD_BYTES;
		final NetworkedProperty[] propertyTable = networkedRegistry.getProperties();
		final int propertyTableSize = propertyTable.length;

		int chunkSizeBytes = 0;
		int chunkStartIndex = 0;
		
		for(int i = 0; i < propertyTableSize; ++i) {
			NetworkedProperty data = propertyTable[i];
			// This is a large overestimation
			int dataSize = 4 + 4 + 4 + 4 * data.allowedValueIndices().length;
			if(chunkSizeBytes + dataSize > maxSizeBytes) {
				// Copy a section of the table with range [chunkStartIndex, i); that is, chunkStartIndex inclusive, i exclusive
				NetworkedProperty[] chunk = Arrays.copyOfRange(propertyTable, chunkStartIndex, i);
				
				// Create & send the packet
				ChunkedBlockRegistryPropertyPacket chunkPacket = new ChunkedBlockRegistryPropertyPacket(uuid, chunkStartIndex, chunk);
				consumer.accept(createPacket(ChunkedBlockRegistryPropertyPacket.TYPE, chunkPacket, ChunkedBlockRegistryPropertyPacket::encode));
				
				// Start a new chunk at this index
				chunkSizeBytes = 0;
				chunkStartIndex = i;
			}
			
			chunkSizeBytes += dataSize;
		}
		
		// Send the final chunk
		
		// Get the final section of the array
		NetworkedProperty[] chunk = Arrays.copyOfRange(propertyTable, chunkStartIndex, propertyTableSize);
		
		// Create & send the final packet
		ChunkedBlockRegistryPropertyPacket chunkPacket = new ChunkedBlockRegistryPropertyPacket(uuid, chunkStartIndex, chunk);
		consumer.accept(createPacket(ChunkedBlockRegistryPropertyPacket.TYPE, chunkPacket, ChunkedBlockRegistryPropertyPacket::encode));
	}
	
	public void sendPropertyRegistry(Consumer<Packet<?>> consumer, UUID uuid, NetworkedPropertyRegistry networkedRegistry) {
		sendPropertyClassesTable(consumer, uuid, networkedRegistry);
		sendPropertyStringTable(consumer, uuid, networkedRegistry);
		sendPropertyTable(consumer, uuid, networkedRegistry);
	}
	
	public void sendChunkedRegistryData(Consumer<Packet<?>> consumer) {
		final UUID uuid = this.chunkedRegistryUUID.get();
		
		final BlockInfoRegistry registry = this.chunkedRegistryCache.get();
		final NetworkedPropertyRegistry networkedRegistry = registry.getNetworkedPropertyRegistry();
		
		final RegistryBlockInfoWrapper.Impl[] blocks = registry.getBlocks();
		
		final int blockCount = blocks.length;

		// defer sending packets, so some cursed memory connection doesn't cause the response to be received before this function finishes executing
		List<Packet<?>> packets = new ArrayList<Packet<?>>();
		
		Consumer<Packet<?>> packetAdder = (packet) -> {
			packets.add(packet);
			this.chunkedDataPacketsRemaining.incrementAndGet();
		};
		
		try {
			// send properties in chunks
			sendPropertyRegistry(packetAdder, uuid, networkedRegistry);
			
			// send blocks in chunks
			{
				final int blockChunks = blockCount / BLOCK_CHUNKING_THRESHOLD;

				int counter = 0;
				for(int i = 0; i <= blockChunks; ++i) {
					final int from = counter;
					final int to = (counter += BLOCK_CHUNKING_THRESHOLD);
					
					RegistryBlockInfoWrapper.Impl[] blockInfoChunk = Arrays.copyOfRange(blocks, from, Math.min(to, blockCount));
					ChunkedBlockRegistryBlockInfoPacket blockInfoPacket = new ChunkedBlockRegistryBlockInfoPacket(uuid, from, blockInfoChunk);
	
					packets.add(createPacket(ChunkedBlockRegistryBlockInfoPacket.TYPE, blockInfoPacket, ChunkedBlockRegistryBlockInfoPacket::encode));
					
					this.chunkedDataPacketsRemaining.incrementAndGet();
				}
			}
		} finally {
			final int toSendCount = this.chunkedDataPacketsRemaining.get();
			for(int i = 0; i < toSendCount; ++i) {
				consumer.accept(packets.get(i));
			}
		}
	}

	// returns true if it's sent the completion packet (this task can now be marked complete), or false if not
	public boolean handleChunkedRegistryDataResponse(Consumer<Packet<?>> consumer) {
		if(this.chunkedDataPacketsRemaining.decrementAndGet() == 0) {
			ChunkedBlockRegistryCompletePacket completePacket = new ChunkedBlockRegistryCompletePacket(this.chunkedRegistryUUID.get());
			consumer.accept(createPacket(ChunkedBlockRegistryCompletePacket.TYPE, completePacket, ChunkedBlockRegistryCompletePacket::encode));
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public Type<?> type() {
		return TYPE;
	}
}
