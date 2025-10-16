
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.io.File;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.extension.MixinExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.configuration.mods.ArtifactMetadata;
import net.fabricmc.loom.task.AbstractRemapJarTask;
import net.fabricmc.loom.task.RemapJarTask;
import net.fabricmc.loom.task.service.ClientEntriesService;
import net.fabricmc.loom.task.service.TinyRemapperService;
import net.fabricmc.loom.task.service.MixinRefmapService;

// this is mostly a copy of net.fabricmc.loom.task.RemapJarTask, except without the code that prevents you from reusing it
public abstract class UsableRemapJarTask extends AbstractRemapJarTask {
	@InputFiles
	public abstract ConfigurableFileCollection getNestedJars();

	@Input
	public abstract Property<Boolean> getAddNestedDependencies();
	
	/**
	 * Whether to optimize the fabric.mod.json file, by default this is false.
	 *
	 * <p>The schemaVersion entry will be placed first in the json file
	 */
	@Input
	public abstract Property<Boolean> getOptimizeFabricModJson();

	@Nested
	public abstract Property<TinyRemapperService.Options> getTinyRemapperServiceOptions();
	
	@Nested
	public abstract ListProperty<MixinRefmapService.Options> getMixinRefmapServiceOptions();

	static List<String> getRootPaths(Set<File> files) {
		return files.stream()
				.map(root -> {
					String rootPath = root.getAbsolutePath().replace("\\", "/");

					if (rootPath.charAt(rootPath.length() - 1) != '/') {
						rootPath += '/';
					}

					return rootPath;
				}).toList();
	}
	
	static Function<File, String> relativePath(List<String> rootPaths) {
		return file -> {
			String s = file.getAbsolutePath().replace("\\", "/");

			for (String rootPath : rootPaths) {
				if (s.startsWith(rootPath)) {
					s = s.substring(rootPath.length());
				}
			}

			return s;
		};
	}
	
	public static Provider<List<MixinRefmapService.Options>> createMixinRefmapServiceOptions(Project project) {
		return project.provider(() -> {
			final LoomGradleExtension extension = LoomGradleExtension.get(project);

			if (!extension.getMixin().getUseLegacyMixinAp().get()) {
				return List.of();
			}

			final MixinExtension mixinExtension = extension.getMixin();

			List<Provider<MixinRefmapService.Options>> options = new ArrayList<>();

			for (SourceSet sourceSet : mixinExtension.getMixinSourceSets()) {
				MixinExtension.MixinInformationContainer container = Objects.requireNonNull(
						MixinExtension.getMixinInformationContainer(sourceSet)
				);

				final List<String> rootPaths = getRootPaths(sourceSet.getResources().getSrcDirs());

				final String refmapName = container.refmapNameProvider().get();
				final List<String> mixinConfigs = container.sourceSet().getResources()
						.matching(container.mixinConfigPattern())
						.getFiles()
						.stream()
						.map(relativePath(rootPaths))
						.toList();

				options.add(
						MixinRefmapService.TYPE.create(project, o -> {
							o.getMixinConfigs().set(mixinConfigs);
							o.getRefmapName().set(refmapName);
						})
					);
			}

			return options.stream().map(Provider::get).toList();
		});
	}
	
	@Inject
	public UsableRemapJarTask() {
		super();
		getAddNestedDependencies().convention(true).finalizeValueOnRead();
		getOptimizeFabricModJson().convention(false).finalizeValueOnRead();
		
		// Make outputs reproducible by default
		setReproducibleFileOrder(true);
		setPreserveFileTimestamps(false);

		getJarType().set("classes");

		getTinyRemapperServiceOptions().set(TinyRemapperService.createOptions(this));
		getMixinRefmapServiceOptions().set(createMixinRefmapServiceOptions(this.getProject()));
	}

	@Override
	protected Provider<? extends ClientEntriesService.Options> getClientOnlyEntriesOptionsProvider(SourceSet clientSourceSet) {
		return ClientEntriesService.Classes.createOptions(getProject(), clientSourceSet);
	}
	
	@TaskAction
	public void run() {
		submitWork(RemapJarTask.RemapAction.class, params -> {
			if (this.getAddNestedDependencies().get()) {
				params.getNestedJars().from(this.getNestedJars());
			}

			if (!params.namespacesMatch()) {
				params.getTinyRemapperServiceOptions().set(this.getTinyRemapperServiceOptions());
				params.getMixinRefmapServiceOptions().set(this.getMixinRefmapServiceOptions());

				params.getRemapClasspath().from(this.getClasspath());

				final var mixinAp = LoomGradleExtension.get(getProject()).getMixin().getUseLegacyMixinAp().get();
				params.getUseMixinExtension().set(!mixinAp);

				// Add the mixin refmap remap type to the manifest
				// This is used by the mod dependency remapper to determine if it should remap the refmap
				// or if the refmap should be remapped by mixin at runtime.
				final var refmapRemapType = mixinAp ? ArtifactMetadata.MixinRemapType.MIXIN : ArtifactMetadata.MixinRemapType.STATIC;
				params.getManifestAttributes().put(Constants.Manifest.MIXIN_REMAP_TYPE, refmapRemapType.manifestValue());
			}

			params.getOptimizeFmj().set(this.getOptimizeFabricModJson().get());
		});
	}
	
	public void addMixinRefmap(List<String> mixinConfigs, String refmapName) {
		var option = MixinRefmapService.TYPE.create(this.getProject(), o -> {
			o.getMixinConfigs().set(mixinConfigs);
			o.getRefmapName().set(refmapName);
		});
		getMixinRefmapServiceOptions().add(option);
	}
}
