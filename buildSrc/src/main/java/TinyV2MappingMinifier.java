import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class TinyV2MappingMinifier {

	private final String firstNamespace;
	private final String secondNamespace;
	
	public TinyV2MappingMinifier(String firstNamespace, String secondNamespace) {
		this.firstNamespace = firstNamespace;
		this.secondNamespace = secondNamespace;
	}
	
	public String getFirstNamespace() {
		return this.firstNamespace;
	}

	public String getSecondNamespace() {
		return this.secondNamespace;
	}
	
	public void minifyFromTo(File sourceFile, File outputFile) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(sourceFile.toPath()); BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
			String line = reader.readLine();

			int firstNamespaceIndex;
			int secondNamespaceIndex;
			
			{
				// Handle header line
				String[] headerLine = line.split("\t");
				if(!headerLine[0].equalsIgnoreCase("tiny")) {
					throw new IOException("Unknown/malformed header " + headerLine[0]);
				}
				
				if(!headerLine[1].equalsIgnoreCase("2")) {
					throw new IOException("Unknown major version " + headerLine[1]);
				}
				
				int minorVersion;
				try {
					minorVersion = Integer.parseInt(headerLine[2], 10);
				} catch (NumberFormatException e) {
					throw new IOException("Unknown/malformed minor version " + headerLine[2], e);
				}
	
				firstNamespaceIndex = -1;
				secondNamespaceIndex = -1;
				
				for(int i = 3; i < headerLine.length; ++i) {
					String mappingName = headerLine[i];
					if(firstNamespaceIndex == -1 && mappingName.equals(this.firstNamespace)) {
						firstNamespaceIndex = i - 2;
					}
					if(secondNamespaceIndex == -1 && mappingName.equals(this.secondNamespace)) {
						secondNamespaceIndex = i - 2;
					}
				}
				
				if(firstNamespaceIndex == -1 || secondNamespaceIndex == -1) {
					throw new IOException("Header missing \"" + this.firstNamespace + "\" or \"" + this.secondNamespace + "\" mappings: \"" + line + "\"");
				}
				
				// Write new header line
				String outputHeader = String.format("tiny\t2\t%d\t%s\t%s\n", minorVersion, this.firstNamespace, this.secondNamespace);
				writer.write(outputHeader);
			}
			
			while((line = reader.readLine()) != null) {
				String[] lineContents = line.split("\t");
				
				String lineType = lineContents[0];
				// Only classes are supported currently
				// Supporting fields, methods, and method parameters would require remapping their descriptors
				// We also strip comments on purpose
				if(lineType.equals("c")) {
					String outputLine = String.format("c\t%s\t%s\n", lineContents[firstNamespaceIndex], lineContents[secondNamespaceIndex]);
					writer.write(outputLine);
				}
			}
		}
	}
}
