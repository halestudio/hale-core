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
package eu.esdihumboldt.hale.io.geoparquet

import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

import groovy.json.JsonSlurper

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import org.junit.Assume

/**
 * Validates GeoParquet files with external tools:
 * <ul>
 * <li><code>gpio</code> (geoparquet-io): <code>gpio check spec</code></li>
 * <li><code>portolan</code> (portolan-cli): the file is added to a temporary
 * catalog and the catalog is checked</li>
 * <li>GDAL's <code>validate_geoparquet.py</code>, run in a Docker container -
 * optional, only if <code>docker</code> is available and the environment
 * variable <code>GEOPARQUET_GDAL_IMAGE</code> is set, e.g. to
 * <code>ghcr.io/osgeo/gdal:ubuntu-full-latest</code></li>
 * </ul>
 * The Python tools are provided by <code>mise</code> (see <code>mise.toml</code>
 * in the repository root). The test JVM inherits <code>PATH</code> from the
 * Gradle daemon, so after <code>mise install</code> of new tools the daemon must
 * be restarted (<code>./gradlew --stop</code>), otherwise the tools are not
 * found.
 * <p>
 * If a required tool is missing the validation is skipped (JUnit assumption)
 * unless the environment variable <code>CI</code> is set, in which case it
 * fails.
 * <p>
 * Note that <code>gpio</code> downloads the DuckDB <code>spatial</code>
 * extension at runtime and the GDAL validation script downloads the GeoParquet
 * JSON schema, so both need network access.
 */
class ExternalValidators {

	/** Timeout for a single tool invocation in seconds. */
	static final int TIMEOUT_SECONDS = 180

	/** Environment variable that enables the GDAL validation, names the image. */
	static final String GDAL_IMAGE_ENV = 'GEOPARQUET_GDAL_IMAGE'

	/**
	 * Portolan rules that are ignored, as they are about the metadata of the
	 * (minimal) test catalog, not about the data:
	 * <ul>
	 * <li><code>PTL-PRV-001</code>: collection declares no providers</li>
	 * <li><code>PTL-FIL-005</code>: README does not mention the data's provenance</li>
	 * </ul>
	 * Any other finding of severity error and any <code>PTL-DAT-*</code> finding
	 * fails the validation.
	 */
	static final Set<String> PORTOLAN_IGNORED_RULES = ['PTL-PRV-001', 'PTL-FIL-005'] as Set

	/**
	 * Portolan rule that is ignored for files with a 3D column bbox
	 * (<code>[xmin, ymin, zmin, xmax, ymax, zmax]</code>, as required by the
	 * GeoParquet specification for 3D geometries): Portolan 0.8.0 reads the first
	 * four values of the bbox as 2D bbox (<code>_extract_bbox</code> in
	 * <code>portolan_cli/metadata/geoparquet.py</code>), so it reports a mismatch
	 * between data and declared bbox. The bbox is verified by
	 * {@link GeoParquetCompliance} instead.
	 */
	static final String PORTOLAN_3D_BBOX_RULE = 'PTL-DAT-005'

	/**
	 * Determine if any geometry column of the <code>geo</code> metadata has a 3D
	 * bbox (6 values).
	 */
	static boolean hasThreeDimensionalBbox(Map geo) {
		((Map) geo.columns)?.values()?.any { Map c -> c.bbox instanceof List && ((List) c.bbox).size() == 6 }
	}

	private static final Map<String, Boolean> AVAILABLE = new ConcurrentHashMap<>()

	/**
	 * Result of a process invocation.
	 */
	static class Result {
		int exitCode
		String output

		@Override
		String toString() {
			"exit code $exitCode, output:\n$output"
		}
	}

	/**
	 * Check if a command line tool is available, by running
	 * <code>&lt;tool&gt; --version</code> (the result is cached).
	 */
	static boolean isAvailable(String tool) {
		AVAILABLE.computeIfAbsent(tool) { String t ->
			try {
				run([t, '--version'], null, 60).exitCode == 0
			} catch (IOException | RuntimeException | AssertionError e) {
				false
			}
		}
	}

	/**
	 * Ensure a tool is available: fails if the <code>CI</code> environment
	 * variable is set, otherwise the test is skipped (assumption).
	 */
	static void assumeAvailable(String tool) {
		if (isAvailable(tool)) {
			return
		}
		if (System.getenv('CI')) {
			fail("Required external validation tool '$tool' not found on PATH (CI is set)")
		}
		Assume.assumeTrue("External validation tool '$tool' not available", false)
	}

	/**
	 * Validate a GeoParquet file with all external validators. Files without
	 * <code>geo</code> metadata (plain Parquet) are not validated.
	 *
	 * @param file the file to validate
	 * @param ignorePortolanRules additional Portolan rule ids that are ignored
	 *   for this file, e.g. <code>PTL-DAT-006</code> for deliberately unsorted data
	 */
	static void check(File file, Collection<String> ignorePortolanRules = []) {
		def geo = GeoParquetTestUtil.geoMetadata(file)
		if (geo == null) {
			return
		}
		checkGpio(file)
		if (geo.version in ['1.1.0', '2.0.0']) {
			Collection<String> ignored = ignorePortolanRules
			if (hasThreeDimensionalBbox(geo)) {
				ignored = ignored + [PORTOLAN_3D_BBOX_RULE]
			}
			checkPortolan(file, ignored)
		}
		checkGdal(file)
	}

	/**
	 * Run <code>gpio check spec</code>, must exit with code 0.
	 */
	static void checkGpio(File file) {
		assumeAvailable('gpio')
		Result r = run([
			'gpio',
			'check',
			'spec',
			file.absolutePath
		], null, TIMEOUT_SECONDS)
		assertTrue("gpio check spec failed for ${file.name}: $r", r.exitCode == 0)
	}

	/**
	 * Add the file to a temporary Portolan catalog, and check the catalog.
	 * Findings regarding the catalog metadata (that is not what is tested here)
	 * are ignored.
	 */
	static void checkPortolan(File file, Collection<String> ignorePortolanRules = []) {
		assumeAvailable('portolan')
		Path dir = Files.createTempDirectory('portolan')
		try {
			File catalog = new File(dir.toFile(), 'cat')
			assertTrue(catalog.mkdirs())
			File data = new File(catalog, 'data')
			assertTrue(data.mkdirs())
			Files.copy(file.toPath(), new File(data, file.name).toPath(), StandardCopyOption.REPLACE_EXISTING)

			Result r = run([
				'portolan',
				'init',
				catalog.absolutePath,
				'--auto',
				'-t',
				'Test',
				'--license',
				'CC0-1.0'
			], dir.toFile(), TIMEOUT_SECONDS)
			assertTrue("portolan init failed: $r", r.exitCode == 0)

			r = run([
				'portolan',
				'add',
				"data/${file.name}"
			], catalog, TIMEOUT_SECONDS)
			assertTrue("portolan add failed for ${file.name}: $r", r.exitCode == 0)
			assertTrue("portolan add reports no geometry for ${file.name}: $r", !r.output.contains('has no geometry'))

			r = run(['portolan', 'check', '--json'], catalog, TIMEOUT_SECONDS)
			evaluatePortolanCheck(file.name, r, ignorePortolanRules)
		} finally {
			dir.toFile().deleteDir()
		}
	}

	/**
	 * Evaluate the output of <code>portolan check --json</code>. The exit code is
	 * not evaluated, as the catalog metadata (that is not complete) causes errors
	 * (also <code>success</code> is <code>false</code> then); only the findings
	 * are evaluated. Fails closed: the report must have a findings list and must
	 * have checked files, otherwise (e.g. an error envelope with empty
	 * <code>data</code>) the check fails.
	 *
	 * @param fileName the name of the checked file, for messages
	 * @param r the result of the check
	 * @param ignorePortolanRules additional Portolan rule ids that are ignored
	 * @throws AssertionError if the report is incomplete or has relevant findings
	 */
	static void evaluatePortolanCheck(String fileName, Result r, Collection<String> ignorePortolanRules = []) {
		int start = r.output.indexOf('{')
		assertTrue("portolan check did not produce JSON: $r", start >= 0)
		def report
		try {
			report = new JsonSlurper().parseText(r.output.substring(start))
		} catch (RuntimeException e) {
			throw new AssertionError("portolan check did not produce valid JSON: $r", e)
		}
		def data = report instanceof Map ? report.data : null
		def findings = data instanceof Map ? data.findings : null
		assertTrue("portolan check reports no findings list for ${fileName}: $r", findings instanceof List)
		def filesChecked = data.files_checked
		assertTrue("portolan check reports no files checked for ${fileName}: $r",
				filesChecked instanceof Number && filesChecked >= 1)
		// Findings of the report (rashid) have the properties rule_id, severity, message, path and object_id.
		// Fail closed: every finding with severity error and every PTL-DAT-* finding (e.g. PTL-DAT-007
		// if statistics are not usable) fails, unless the rule is explicitly ignored.
		Set<String> ignored = PORTOLAN_IGNORED_RULES + (ignorePortolanRules as Set)
		def relevant = ((List) findings).findAll { f ->
			!(f.rule_id in ignored) && (f.severity == 'error' || (f.rule_id as String)?.startsWith('PTL-DAT'))
		}
		assertTrue("portolan check reports findings for ${fileName}: ${relevant}\n$r", relevant.isEmpty())
	}

	/**
	 * Run GDAL's GeoParquet validation script in a Docker container, if enabled
	 * (<code>docker</code> available and <code>GEOPARQUET_GDAL_IMAGE</code> set).
	 * Never fails because of missing tools. If <code>https_proxy</code> is set
	 * in the environment it is passed into the container (using host
	 * networking), as the script downloads the GeoParquet JSON schema.
	 */
	static void checkGdal(File file) {
		String image = System.getenv(GDAL_IMAGE_ENV)
		if (!image || !isAvailable('docker')) {
			return
		}
		List<String> cmd = ['docker', 'run', '--rm']
		String proxy = System.getenv('https_proxy') ?: System.getenv('HTTPS_PROXY')
		if (proxy) {
			cmd += [
				'--network',
				'host',
				'-e',
				"https_proxy=$proxy"
			]
		}
		cmd += [
			'-v',
			"${file.absoluteFile.parentFile.absolutePath}:/data:ro",
			image,
			'python3',
			'/usr/lib/python3/dist-packages/osgeo_utils/samples/validate_geoparquet.py',
			'--check-data',
			"/data/${file.name}"
		]
		Result r = run(cmd, null, TIMEOUT_SECONDS)
		assertTrue("GDAL validate_geoparquet failed for ${file.name}: $r", r.exitCode == 0)
	}

	/**
	 * Run a process, with combined stdout/stderr captured and a timeout.
	 */
	static Result run(List<String> command, File dir, int timeoutSeconds) {
		File out = File.createTempFile('validator', '.log')
		try {
			ProcessBuilder pb = new ProcessBuilder(command.collect { it.toString() })
			if (dir != null) {
				pb.directory(dir)
			}
			pb.redirectErrorStream(true)
			pb.redirectOutput(out)
			Process p = pb.start()
			// no input
			p.outputStream.close()
			if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
				p.destroyForcibly()
				p.waitFor()
				throw new AssertionError("Command timed out after $timeoutSeconds s: ${command.join(' ')}\n${out.getText('UTF-8')}")
			}
			new Result(exitCode: p.exitValue(), output: new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8))
		} finally {
			out.delete()
		}
	}
}
