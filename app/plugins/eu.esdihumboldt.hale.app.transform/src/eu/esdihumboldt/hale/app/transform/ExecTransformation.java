/*
 * Copyright (c) 2014 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.app.transform;

import static eu.esdihumboldt.hale.app.transform.ExecUtil.error;
import static eu.esdihumboldt.hale.app.transform.ExecUtil.fail;
import static eu.esdihumboldt.hale.app.transform.ExecUtil.info;
import static eu.esdihumboldt.hale.app.transform.ExecUtil.status;
import static eu.esdihumboldt.hale.app.transform.ExecUtil.warn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.eclipse.core.runtime.content.IContentType;

import com.google.common.util.concurrent.ListenableFuture;

import eu.esdihumboldt.hale.common.core.io.HaleIO;
import eu.esdihumboldt.hale.common.core.io.extension.IOProviderDescriptor;
import eu.esdihumboldt.hale.common.core.io.project.model.IOConfiguration;
import eu.esdihumboldt.hale.common.core.io.supplier.DefaultInputSupplier;
import eu.esdihumboldt.hale.common.core.io.supplier.FileIOSupplier;
import eu.esdihumboldt.hale.common.core.io.supplier.Locatable;
import eu.esdihumboldt.hale.common.core.io.supplier.LocatableInputSupplier;
import eu.esdihumboldt.hale.common.core.io.supplier.LocatableOutputSupplier;
import eu.esdihumboldt.hale.common.headless.impl.ProjectTransformationEnvironment;
import eu.esdihumboldt.hale.common.headless.transform.DefaultTransformationSettings;
import eu.esdihumboldt.hale.common.headless.transform.Transformation;
import eu.esdihumboldt.hale.common.headless.transform.TransformationSettings;
import eu.esdihumboldt.hale.common.instance.io.InstanceReader;
import eu.esdihumboldt.hale.common.instance.io.InstanceValidator;
import eu.esdihumboldt.hale.common.instance.io.InstanceWriter;
import eu.esdihumboldt.util.groovy.collector.StatsCollector;
import eu.esdihumboldt.util.groovy.sandbox.DefaultGroovyService;
import eu.esdihumboldt.util.groovy.sandbox.GroovyService;
import groovy.json.JsonOutput;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.util.DelegatingScript;

/**
 * Executes a transformation.
 *
 * @author Simon Templer
 */
public class ExecTransformation implements ConsoleConstants {

	/**
	 * Visitor that collects files to be included.
	 */
	private static final class DirVisitor implements FileVisitor<Path> {

		private final List<Path> collectedFiles = new ArrayList<>();
		private final Path parentDir;
		private final List<PathMatcher> includes;
		private final List<PathMatcher> excludes;

		/**
		 * Constructor.
		 *
		 * @param parentDir the parent directory
		 * @param includes the include patterns
		 * @param excludes the exclude patterns
		 */
		public DirVisitor(Path parentDir, List<String> includes, List<String> excludes) {
			this.parentDir = parentDir;
			this.includes = new ArrayList<>(includes.size());
			for (String pattern : includes) {
				PathMatcher matcher = parentDir.getFileSystem().getPathMatcher("glob:" + pattern);
				this.includes.add(matcher);
			}
			this.excludes = new ArrayList<>(excludes.size());
			for (String pattern : excludes) {
				PathMatcher matcher = parentDir.getFileSystem().getPathMatcher("glob:" + pattern);
				this.excludes.add(matcher);
			}
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
				throws IOException {
			/*
			 * XXX currently cannot determine from the patterns if a directory should be
			 * inspected or not
			 */
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (accept(file)) {
				collectedFiles.add(file);
			}
			return FileVisitResult.CONTINUE;
		}

		private boolean accept(Path file) {
			Path relative = parentDir.relativize(file);

			boolean included = false;
			for (PathMatcher include : includes) {
				if (include.matches(relative)) {
					included = true;
					break;
				}
			}

			if (!included) {
				return false;
			}

			for (PathMatcher exclude : excludes) {
				if (exclude.matches(relative)) {
					return false;
				}
			}

			return true;
		}

		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
			// ignore, but log
			warn("Could not access file " + file);
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			return FileVisitResult.CONTINUE;
		}

		/**
		 * @return the list of files collected from the directory
		 */
		public List<Path> getCollectedFiles() {
			return Collections.unmodifiableList(collectedFiles);
		}
	}

	/**
	 * ID for the transformation
	 */
	private final String id = UUID.randomUUID().toString();

	private TransformationReports reportHandler = null;

	private ProjectTransformationEnvironment env;

	private ExecContext context;

	private InstanceWriter target;

	private final List<InstanceReader> sources = new ArrayList<InstanceReader>();

	private final Collection<InstanceValidator> validators = new ArrayList<>();

	@SuppressWarnings("javadoc")
	public int run(ExecContext context) throws Exception {
		this.context = context;

		new ConsoleProgressManager();

		// set up report handler
		this.reportHandler = new TransformationReports(context.getReportsOut());

		// set up transformation environment
		loadProject();

		// set up reader for source
		Iterator<URI> sourceIt = context.getSources().iterator();
		int index = 0;
		while (sourceIt.hasNext()) {
			URI uri = sourceIt.next();

			Path path;
			try {
				path = Paths.get(uri);
			} catch (Exception e) {
				path = null;
			}

			if (path != null && java.nio.file.Files.isDirectory(path)) {
				// directory handling
				List<Path> files = getIncludedFiles(path, index);
				info(MessageFormat.format("{0} files identified for source {1}", files.size(),
						path));
				for (Path file : files) {
					setupReader(file.toUri(), index);
				}
			}
			else {
				// file / URI handling
				setupReader(uri, index);
			}

			// increase source index
			index++;
		}

		// set up writer for target
		setupWriter();

		// set up the target validators (if any)
		setupValidators();

		if (target == null) {
			// writer could not be created
			// return error code
			return 1;
		}

		// trigger transformation
		return transform();
	}

	/**
	 * Get the files to load from a directory.
	 *
	 * @param parentDir the directory
	 * @param index the source index
	 * @return the list of file
	 */
	private List<Path> getIncludedFiles(Path parentDir, int index) {
		List<String> includes = context.getSourceIncludes().get(index);
		if (includes.isEmpty()) {
			// default include - all files
			includes.add("**");
		}

		List<String> excludes = context.getSourceExcludes().get(index);

		DirVisitor visitor = new DirVisitor(parentDir, includes, excludes);
		try {
			java.nio.file.Files.walkFileTree(parentDir, visitor);
		} catch (IOException e) {
			throw new IllegalStateException("Error browsing source directory " + parentDir, e);
		}
		return visitor.getCollectedFiles();
	}

	private void loadProject() throws IOException {
		status("Loading hale project...");

		Map<Class<?>, Object> services = new HashMap<>();
		// override/set Groovy service
		GroovyService gs = new DefaultGroovyService();
		gs.setRestrictionActive(context.isRestrictGroovy());
		services.put(GroovyService.class, gs);

		env = new ProjectTransformationEnvironment(id,
				new DefaultInputSupplier(context.getProject()), reportHandler, null, services);
	}

	private void setupReader(URI uri, int index) {
		LocatableInputSupplier<? extends InputStream> sourceIn = new DefaultInputSupplier(uri);

		// create I/O provider
		InstanceReader source = null;
		String customProvider = context.getSourceProviderIds().get(index);
		if (customProvider != null) {
			// use specified provider
			source = HaleIO.createIOProvider(InstanceReader.class, null, customProvider);
			if (source == null) {
				fail("Could not find instance reader with ID " + customProvider);
			}
		}
		if (source == null) {
			// find applicable reader
			source = HaleIO.findIOProvider(InstanceReader.class, sourceIn, uri.getPath());
		}
		if (source == null) {
			throw fail("Could not determine instance reader to use for source data");
		}

		// apply custom settings
		source.loadConfiguration(context.getSourcesSettings().get(index));

		source.setSource(sourceIn);
		// source schema is set in Transformation.transform
		// CRS provider is set in headless transformation

		sources.add(source);
	}

	private void setupWriter() {
		String preset = context.getPreset();
		String customProvider = context.getTargetProviderId();
		if (preset == null && customProvider == null) {
			fail("Please specify the name of a data export configuration preset or provide a specific provider ID for the instance writer");
		}

		// create I/O configuration
		IOConfiguration conf = null;
		if (preset != null) {
			conf = env.getExportPresets().get(preset);
		}
		if (conf == null) {
			if (customProvider == null) {
				throw fail("Data export configration preset not found: " + preset
						+ " (please make sure you created it and saved it as part of the project)");
			}
			else {
				conf = new IOConfiguration();
			}
		}
		if (customProvider != null) {
			conf.setProviderId(customProvider);
		}

		// apply custom settings to configuration
		conf.getProviderConfiguration().putAll(context.getTargetSettings());

		// create I/O provider
		String writerId = conf.getProviderId();
		target = HaleIO.createIOProvider(InstanceWriter.class, null, writerId);
		target.setTarget(createTargetSupplier(context.getTarget()));
		target.setTargetSchema(env.getTargetSchema());

		// determine content type to use based on file extension
		IOProviderDescriptor factory = HaleIO.findIOProviderFactory(InstanceWriter.class, null,
				writerId);
		if (factory == null) {
			throw fail("Instance writer with ID " + writerId + " not found");
		}
		String path = context.getTarget().getPath();
		List<IContentType> cts;
		if (path != null) {
			cts = HaleIO.findContentTypesFor(factory.getSupportedTypes(), null, path);
		}
		else {
			cts = new ArrayList<>(factory.getSupportedTypes());
		}
		if (!cts.isEmpty()) {
			target.setContentType(cts.get(0));
		}

		// apply configuration (may override content type)
		target.loadConfiguration(conf.getProviderConfiguration());
	}

	private void setupValidators() {
		if (context.getValidateProviderIds() != null
				&& !context.getValidateProviderIds().isEmpty()) {

			for (int i = 0; i < context.getValidateProviderIds().size(); i++) {
				String validateProviderId = context.getValidateProviderIds().get(i);

				if (!validateProviderId.trim().isEmpty()) {
					final InstanceValidator validator = HaleIO
							.createIOProvider(InstanceValidator.class, null, validateProviderId);
					if (validator == null) {
						throw fail(
								"Instance validator with ID " + validateProviderId + " not found");
					}

					// load validator settings
					validator.loadConfiguration(context.getValidateSettings().get(i));

					// set schemas
					List<? extends Locatable> schemas = target.getValidationSchemas();
					validator.setSchemas(schemas.toArray(new Locatable[schemas.size()]));

					// set source
					validator.setSource(new DefaultInputSupplier(context.getTarget()));

					// apply target content type
					validator.setContentType(target.getContentType());

					this.validators.add(validator);
				}
			}
		}
	}

	private LocatableOutputSupplier<? extends OutputStream> createTargetSupplier(final URI uri) {
		try {
			File file = new File(uri);
			return new FileIOSupplier(file);
		} catch (IllegalArgumentException e) {
			// TODO check for other supported URI types, e.g. FTP?

			// create dummy output supplier
			// e.g. for JDBC URIs
			return new LocatableOutputSupplier<OutputStream>() {

				@Override
				public OutputStream getOutput() throws IOException {
					throw new UnsupportedOperationException();
				}

				@Override
				public URI getLocation() {
					return uri;
				}
			};
		}
	}

	private int transform() throws InterruptedException, ExecutionException {
		status("Running hale transformation...");

		TransformationSettings settings = new DefaultTransformationSettings();
		// TODO make settings configurable?

		// run transformation
		ListenableFuture<Boolean> res = Transformation.transform(sources, target, env,
				reportHandler, id, validators, context.getFilters(), settings);

		boolean orgSuccess = res.get();

		// Job threads might still be active, wait a moment to allow
		// them to complete and file their report (otherwise error may get lost)
		try {
			Thread.sleep(3000);
		} catch (Throwable e) {
			// ignore
		}

		boolean success;
		try {
			success = evaluateSuccess(orgSuccess);
		} catch (Throwable e) {
			error("Success evaluation resulted in an error:\n" + e.getMessage());
			if (context.isLogException()) {
				e.printStackTrace();
			}
			return 2;
		}

		if (success) {
			info("Transformation completed. Please check the reports for more details.");
		}
		else {
			if (orgSuccess) {
				error("Transformation failed according to the success evaluation script.");
				return 2;
			}
			else {
				error("Transformation failed, please check the reports for details.");
				return 1;
			}
		}

		// exit OK
		return 0;
	}

	private boolean evaluateSuccess(boolean success) {
		StatsCollector statistics = reportHandler.getStatistics();

		if (context.getStatisticsFile() != null) {
			// write to statistics file
			try (Writer writer = Files.newBufferedWriter(context.getStatisticsFile().toPath(),
					StandardCharsets.UTF_8)) {
				writer.write(JsonOutput.prettyPrint(statistics.saveToJson(true)));
			} catch (IOException e) {
				ExecUtil.error("Error writing statistics file: " + e.getMessage());
				// XXX should this fail the command?
			}
		}

		if (context.getSuccessEvaluationScript() == null) {
			// no custom evaluation script
			return success;
		}

		// run evaluation script

		CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
		compilerConfiguration.setScriptBaseClass(DelegatingScript.class.getName());

		// Configure the GroovyShell and pass the compiler configuration.
		GroovyShell shell = new GroovyShell(getClass().getClassLoader(), new Binding(),
				compilerConfiguration);
		DelegatingScript script;
		try (InputStream in = new DefaultInputSupplier(context.getSuccessEvaluationScript())
				.getInput();
				InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			script = (DelegatingScript) shell.parse(reader);
		} catch (IOException e) {
			ExecUtil.error("Error executing evaluation script:\n" + e.getMessage());
			return false;
		}

		// run against compact map/list structure of statistics
		script.setDelegate(statistics.saveToMapListStructure(true));
		Object res = script.run();
		if (res instanceof Boolean) {
			return ((Boolean) res);
		}
		return true;
	}

}
