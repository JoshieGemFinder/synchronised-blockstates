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
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryPropertyPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.ChunkedBlockRegistryStartPacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.LoginTaskProbePacket;
import com.joshiegemfinder.synchronisedblockstates.common.network.packet.UnchunkedBlockRegistryPacket;
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry;
import com.joshiegemfinder.synchronisedblockstates.common.util.PropertyRepresentative;
import com.joshiegemfinder.synchronisedblockstates.common.util.RegistryBlockInfoWrapper;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public class SyncBlockstatesTask implements Task {
	public static final Task.Type<SyncBlockstatesTask> TYPE = new Task.Type<SyncBlockstatesTask>(new ResourceLocation(SynchronisedBlockstates.MOD_ID, "sync_blockstates_task"));

	public static final int PROPERTY_CHUNKING_THRESHOLD = Integer.getInteger("mod.synchronisedblockstates.propertyChunkingThreshold", 1024);
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
		
		boolean shouldChunkData = registry.getProperties().length > PROPERTY_CHUNKING_THRESHOLD
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
		
		ChunkedBlockRegistryStartPacket startPacket = new ChunkedBlockRegistryStartPacket(this.chunkedRegistryUUID.get(), registry.getProperties().length, registry.getBlocks().length);
		
		consumer.accept(createPacket(ChunkedBlockRegistryStartPacket.TYPE, startPacket, ChunkedBlockRegistryStartPacket::encode));
	}
	
	public void sendChunkedRegistryData(Consumer<Packet<?>> consumer) {
		final UUID uuid = this.chunkedRegistryUUID.get();
		
		final PropertyRepresentative[] properties = this.chunkedRegistryCache.get().getProperties();
		final RegistryBlockInfoWrapper.Impl[] blocks = this.chunkedRegistryCache.get().getBlocks();
		
		final int propertyCount = properties.length;
		final int blockCount = blocks.length;

		// defer sending packets, so some cursed memory connection doesn't cause the response to be received before this function finishes executing
		List<Packet<?>> packets = new ArrayList<Packet<?>>();
		
		try {
			// send properties in chunks
			{
				final int propertyChunks = propertyCount / PROPERTY_CHUNKING_THRESHOLD;
				
				int counter = 0;
				for(int i = 0; i <= propertyChunks; ++i) {
					final int from = counter;
					final int to = (counter += PROPERTY_CHUNKING_THRESHOLD);
					
					PropertyRepresentative[] propertyChunk = Arrays.copyOfRange(properties, from, Math.min(to, propertyCount));
					ChunkedBlockRegistryPropertyPacket propertyPacket = new ChunkedBlockRegistryPropertyPacket(uuid, from, propertyChunk);
	
					packets.add(createPacket(ChunkedBlockRegistryPropertyPacket.TYPE, propertyPacket, ChunkedBlockRegistryPropertyPacket::encode));
					
					this.chunkedDataPacketsRemaining.incrementAndGet();
				}
			}
			
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
