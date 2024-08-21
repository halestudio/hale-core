import groovy.transform.CompileStatic
import org.gradle.api.Action

@CompileStatic
class HaleProjectExtension {
	// internal
	AllureConfig allure = new AllureConfig()

	// OSGI bundle options
	String activator = null
	boolean lazyActivation = false
	String bundleName = null
	String bundleSymbolicName = null
	Boolean singletonBundle = null
	String fragmentHost = null
	String bundleVendor = null
	String privatePackage = null
	String exportPackage = null
	boolean registeredBuddies = false

	void allure(Action<AllureConfig> action) {
		action.execute(allure)
	}

}
