/*
 * Copyright (c) 2026 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.util.resource.internal;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import eu.esdihumboldt.util.io.InputSupplier;
import eu.esdihumboldt.util.resource.ResourceNotFoundException;

/**
 * Tests for {@link BundleResolver} without OSGi.
 *
 * @author Simon Templer
 */
public class BundleResolverTest {

	/**
	 * Temporary folder for creating resource jars and directories
	 */
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/**
	 * Test resolving a resource if a directory with the same path is found first on
	 * the class path in a jar, as for the INSPIRE code list resources (e.g.
	 * <code>codelist/NameStatusValue/</code> in <code>codelists.inspire</code> and
	 * <code>codelist/NameStatusValue</code> in
	 * <code>codelists.inspire.accept-xml</code>).
	 *
	 * @throws Exception if an error occurs
	 */
	@Test
	public void testSkipDirectoryInJar() throws Exception {
		File dirJar = createJar("dir.jar", Map.of("codelist/", "", "codelist/Value/", "",
				"codelist/Value/Value.en.xml", "en"));
		File fileJar = createJar("file.jar", Map.of("codelist/", "", "codelist/Value", "file"));

		try (URLClassLoader loader = createLoader(dirJar, fileJar)) {
			assertEquals("file", read(new BundleResolver(loader), "codelist/Value"));
		}
	}

	/**
	 * Test resolving a resource if a directory with the same path is found first on
	 * the class path in a file system directory.
	 *
	 * @throws Exception if an error occurs
	 */
	@Test
	public void testSkipDirectoryInFolder() throws Exception {
		File folder = tmp.newFolder("folder");
		File valueDir = new File(folder, "codelist/Value");
		valueDir.mkdirs();
		Files.writeString(new File(valueDir, "Value.en.xml").toPath(), "en");
		File fileJar = createJar("file.jar", Map.of("codelist/", "", "codelist/Value", "file"));

		try (URLClassLoader loader = createLoader(folder, fileJar)) {
			assertEquals("file", read(new BundleResolver(loader), "codelist/Value"));
		}
	}

	/**
	 * Test that a resource that is only available as directory is not resolved.
	 *
	 * @throws Exception if an error occurs
	 */
	@Test(expected = ResourceNotFoundException.class)
	public void testOnlyDirectory() throws Exception {
		File dirJar = createJar("dir.jar", Map.of("codelist/", "", "codelist/Value/", "",
				"codelist/Value/Value.en.xml", "en"));

		try (URLClassLoader loader = createLoader(dirJar)) {
			new BundleResolver(loader).resolve(URI.create("http://example.com/codelist/Value"));
		}
	}

	/**
	 * Test resolving a file inside a directory that also exists as file in another
	 * jar.
	 *
	 * @throws Exception if an error occurs
	 */
	@Test
	public void testFileInDirectory() throws Exception {
		File dirJar = createJar("dir.jar", Map.of("codelist/", "", "codelist/Value/", "",
				"codelist/Value/Value.en.xml", "en"));
		File fileJar = createJar("file.jar", Map.of("codelist/", "", "codelist/Value", "file"));

		try (URLClassLoader loader = createLoader(dirJar, fileJar)) {
			assertEquals("en", read(new BundleResolver(loader), "codelist/Value/Value.en.xml"));
		}
	}

	private String read(BundleResolver resolver, String path) throws Exception {
		InputSupplier<? extends InputStream> supplier = resolver
				.resolve(URI.create("http://example.com/" + path));
		try (InputStream in = supplier.getInput()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private URLClassLoader createLoader(File... entries) throws IOException {
		URL[] urls = new URL[entries.length];
		for (int i = 0; i < entries.length; i++) {
			urls[i] = entries[i].toURI().toURL();
		}
		// no parent class loader, so only the given entries are searched
		return new URLClassLoader(urls, null);
	}

	private File createJar(String name, Map<String, String> entries) throws IOException {
		File jar = tmp.newFile(name);
		try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
			// add entries sorted, so directories are added before their content
			for (String entryName : new TreeSet<>(entries.keySet())) {
				out.putNextEntry(new JarEntry(entryName));
				out.write(entries.get(entryName).getBytes(StandardCharsets.UTF_8));
				out.closeEntry();
			}
		}
		return jar;
	}

}
