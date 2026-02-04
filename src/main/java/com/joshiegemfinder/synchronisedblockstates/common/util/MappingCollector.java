package com.joshiegemfinder.synchronisedblockstates.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Objects;

public class MappingCollector {

	public final String runtimeNamespace;
	public final String networkNamespace;

	public MappingCollector(String runtimeNamespace, String networkNamespace) {
		this.runtimeNamespace = Objects.requireNonNull(runtimeNamespace);
		this.networkNamespace = Objects.requireNonNull(networkNamespace);
	}
	
	public void readMappings(InputStream inputStream, ClassNameMappingConsumer runtimeToNetworkMappingConsumer) throws IOException {
		this.readMappings(new InputStreamReader(inputStream), runtimeToNetworkMappingConsumer);
	}
	
	public void readMappings(Reader inputReader, ClassNameMappingConsumer runtimeToNetworkMappingConsumer) throws IOException {
		try (BufferedReader reader = new BufferedReader(inputReader)) {
			String line = reader.readLine();

			int minorFileVersion;

			int runtimeNamespaceIndex;
			int networkNamespaceIndex;
			
			// Handle header line
			{
				String[] headerLine = line.split("\t");
				if(!headerLine[0].equalsIgnoreCase("tiny")) {
					throw new IOException("Unknown/malformed header " + headerLine[0]);
				}
				
				if(!headerLine[1].equalsIgnoreCase("2")) {
					throw new IOException("Unknown major version " + headerLine[1]);
				}
				
				try {
					minorFileVersion = Integer.parseInt(headerLine[2], 10);
				} catch (NumberFormatException e) {
					throw new IOException("Unknown/malformed minor version " + headerLine[2], e);
				}
	
				runtimeNamespaceIndex = -1;
				networkNamespaceIndex = -1;
				
				for(int i = 3; i < headerLine.length; ++i) {
					String mappingName = headerLine[i];
					if(runtimeNamespaceIndex == -1 && mappingName.contentEquals(this.runtimeNamespace)) {
						runtimeNamespaceIndex = i - 2;
					}
					if(networkNamespaceIndex == -1 && mappingName.contentEquals(this.networkNamespace)) {
						networkNamespaceIndex = i - 2;
					}
				}
				
				if(runtimeNamespaceIndex == -1 || networkNamespaceIndex == -1) {
					throw new IOException("Header missing \"" + this.runtimeNamespace + "\" or \"" + this.networkNamespace + "\" mappings: \"" + line + "\"");
				}
			}
			
			// Read mappings from each line
			while((line = reader.readLine()) != null) {
				String[] lineContents = line.split("\t");
				
				String lineType = lineContents[0];
				// We only care about classes
				if(lineType.equals("c")) {
					String runtimeClassDescriptor = lineContents[runtimeNamespaceIndex];
					String networkClassDescriptor = lineContents[networkNamespaceIndex];

					// Convert (e.g. net/minecraft/Minecraft to net.minecraft.Minecraft)
					// TODO Check for a better way to do this conversion
					String runtimeClassName = runtimeClassDescriptor.replace('/', '.');
					String networkClassName = networkClassDescriptor.replace('/', '.');
					
					runtimeToNetworkMappingConsumer.accept(runtimeClassName, networkClassName);
				}
			}
		}
	}
	
	@FunctionalInterface
	public static interface ClassNameMappingConsumer {
		public void accept(String runtimeClassName, String networkClassName);
	}
}
