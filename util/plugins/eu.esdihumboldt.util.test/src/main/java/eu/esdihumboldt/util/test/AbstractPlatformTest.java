package eu.esdihumboldt.util.test;

import org.junit.BeforeClass;

import eu.esdihumboldt.util.nonosgi.Init;

/**
 * Base class for unit tests that makes sure the platform is initialized.
 */
public abstract class AbstractPlatformTest {

	@BeforeClass
	public static void init() {
		Init.init();
	}

}
