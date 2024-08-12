package eu.esdihumboldt.util.test;

import eu.esdihumboldt.util.nonosgi.Init;
import org.junit.BeforeClass;

/**
 * Base class for unit tests that makes sure the platform is initialized.
 */
public abstract class AbstractPlatformTest {

    @BeforeClass
    public static void init() {
        Init.init();
    }

}
