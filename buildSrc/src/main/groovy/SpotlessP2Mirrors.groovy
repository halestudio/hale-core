import org.gradle.api.Project

/**
 * Use an Eclipse mirror for the P2 repositories of the spotless Eclipse formatters, as download.eclipse.org is not
 * reliably available.
 *
 * The mirror base URL can be changed with -PspotlessP2Mirror=<url>, an empty value disables the mirror.
 *
 * Note: buildSrc/build.gradle has a copy of the mirror configuration.
 */
class SpotlessP2Mirrors {

	static final String DEFAULT_MIRROR = 'https://ftp.fau.de/eclipse'

	/**
	 * Configure the P2 mirrors on a spotless Eclipse formatter configuration (e.g. the result of greclipse()).
	 */
	static <T> T configure(Project project, T config) {
		String mirror = project.hasProperty('spotlessP2Mirror') ? project.findProperty('spotlessP2Mirror') : DEFAULT_MIRROR
		if (mirror) {
			config.withP2Mirrors([
				'https://download.eclipse.org/eclipse/updates/': "${mirror}/eclipse/updates/".toString(),
				// every P2 repository needs a mapping as soon as mirrors are configured
				'https://groovy.jfrog.io/artifactory/plugins-release': 'https://groovy.jfrog.io/artifactory/plugins-release'
			])
		}
		config
	}
}
