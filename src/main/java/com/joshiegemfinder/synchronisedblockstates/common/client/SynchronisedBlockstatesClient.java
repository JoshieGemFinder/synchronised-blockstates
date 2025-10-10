package com.joshiegemfinder.synchronisedblockstates.common.client;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import com.joshiegemfinder.synchronisedblockstates.common.SynchronisedBlockstates;
import com.joshiegemfinder.synchronisedblockstates.common.client.util.MappingUtil;
import com.joshiegemfinder.synchronisedblockstates.common.util.BlockInfoRegistry;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.IdMapper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.state.BlockState;

public class SynchronisedBlockstatesClient {

	public static void onInitializeClient() {
		if(shouldDumpAllBlockstates()) {
			dumpVanillaMappings();
		}
	}
	
	public static Path getOutputDirectory() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("synchronized_blockstates");
	}
	
	public static boolean getPropertyBoolean(String property, boolean defaultValue) {
		final String value;
		try {
			value = System.getProperty(property);
			if(value == null) { return defaultValue; }
		} catch(SecurityException e) {
			return defaultValue;
		}
		
		return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("1") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("on");
	}

	public static boolean shouldDumpAllBlockstates() {
		return getPropertyBoolean("mod.synchronisedblockstates.dumpAllStates", false);
	}

	public static boolean shouldOutputMissingEntries() {
		return getPropertyBoolean("mod.synchronisedblockstates.outputMissingBlockstates", false);
	}
	
	public static void dumpVanillaMappings() {
		IdMapper<BlockState> originalMappings = MappingUtil.getOriginalBlockStateRegistry();

		SynchronisedBlockstates.LOGGER.info("Converting local blockstates to registry...");
		final long start = System.nanoTime();
		BlockInfoRegistry registry = BlockInfoRegistry.createRegistry(originalMappings);
		final long end = System.nanoTime();
		SynchronisedBlockstates.LOGGER.info("Converting local blockstates to registry took {} seconds ({} nanos)", TimeUnit.SECONDS.convert(end - start, TimeUnit.NANOSECONDS), end - start);
		
		try {
			Path outputFolderPath = Files.createDirectories(getOutputDirectory());
			File file = outputFolderPath.resolve("vanillablockstates.dat").toFile();
			FileOutputStream stream = new FileOutputStream(file);
			DataOutputStream output = new DataOutputStream(stream);
			
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			
			BlockInfoRegistry.encode(buf, registry);

			ByteBuf read = buf.resetReaderIndex();
			read.readBytes(output, read.readableBytes());
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static BlockInfoRegistry readVanillaBlockstatesOrThrow() throws IOException {
		byte[] bytes = IOUtils.resourceToByteArray("vanillablockstates.dat", Thread.currentThread().getContextClassLoader());
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeBytes(bytes);
		
		BlockInfoRegistry registry = BlockInfoRegistry.decode(buf);

		return registry;
	}
	
	@Nullable
	public static BlockInfoRegistry readVanillaBlockstates() {
		try {
			return readVanillaBlockstatesOrThrow();
		} catch (IOException e) {
			SynchronisedBlockstates.LOGGER.error("Failed to read vanilla blockstates from file", e);
			return null;
		}
	}
}
