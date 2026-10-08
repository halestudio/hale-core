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
package eu.esdihumboldt.hale.common.instance.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;

import de.fhg.igd.eclipse.util.extension.AbstractConfigurationFactory;
import de.fhg.igd.eclipse.util.extension.AbstractExtension;
import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.core.service.ServiceProvider;
import eu.esdihumboldt.hale.common.instance.model.DataSet;

/**
 * Extension for {@link InstanceStoreFactory}s, selects and creates stores.
 */
public class InstanceStoreExtension
		extends AbstractExtension<InstanceStoreFactory, InstanceStoreExtension.Descriptor> {

	/**
	 * The extension point ID.
	 */
	public static final String EXTENSION_ID = "eu.esdihumboldt.hale.instance.store";

	/**
	 * System property naming the store to use.
	 */
	public static final String SYSTEM_PROPERTY = "hale.instance.store";

	private static final ALogger log = ALoggerFactory.getLogger(InstanceStoreExtension.class);

	/**
	 * A store implementation candidate.
	 */
	public interface Candidate {

		/**
		 * @return the store identifier
		 */
		String getId();

		/**
		 * @return the store priority, higher is preferred
		 */
		int getStorePriority();

		/**
		 * @return the store factory
		 * @throws Exception if the factory cannot be created
		 */
		InstanceStoreFactory createFactory() throws Exception;
	}

	/**
	 * Store factory descriptor based on a configuration element.
	 */
	public static class Descriptor extends AbstractConfigurationFactory<InstanceStoreFactory>
			implements Candidate {

		/**
		 * @param conf the configuration element
		 */
		protected Descriptor(IConfigurationElement conf) {
			super(conf, "class");
		}

		@Override
		public void dispose(InstanceStoreFactory instance) {
			// nothing to do
		}

		@Override
		public String getIdentifier() {
			return conf.getAttribute("id");
		}

		@Override
		public String getDisplayName() {
			return getIdentifier();
		}

		@Override
		public int getPriority() {
			return -getStorePriority();
		}

		@Override
		public String getId() {
			return getIdentifier();
		}

		@Override
		public int getStorePriority() {
			try {
				return Integer.parseInt(conf.getAttribute("priority"));
			} catch (Exception e) {
				return 0;
			}
		}

		@Override
		public InstanceStoreFactory createFactory() throws Exception {
			return createExtensionObject();
		}
	}

	private static final InstanceStoreExtension INSTANCE = new InstanceStoreExtension();

	/**
	 * @return the extension instance
	 */
	public static InstanceStoreExtension getInstance() {
		return INSTANCE;
	}

	private InstanceStoreExtension() {
		super(EXTENSION_ID);
	}

	@Override
	protected Descriptor createFactory(IConfigurationElement conf) throws Exception {
		if ("store".equals(conf.getName())) {
			return new Descriptor(conf);
		}
		return null;
	}

	/**
	 * Create a store, using the store named by the system property
	 * {@value #SYSTEM_PROPERTY} if set, otherwise the store with the highest
	 * priority. If creating a store fails, the next store is tried.
	 *
	 * @param dataSet the data set
	 * @param directory the store directory
	 * @param services the service provider, may be <code>null</code>
	 * @return the created store
	 * @throws IOException if no store could be created
	 */
	public InstanceStore createStore(DataSet dataSet, Path directory, ServiceProvider services)
			throws IOException {
		return createStore(getFactories(), System.getProperty(SYSTEM_PROPERTY), dataSet, directory,
				services);
	}

	/**
	 * Order candidates: requested id first, then by descending priority.
	 *
	 * @param candidates the candidates
	 * @param requestedId the requested id, may be <code>null</code>
	 * @return the ordered candidates
	 */
	static List<Candidate> order(List<? extends Candidate> candidates, String requestedId) {
		List<Candidate> result = new ArrayList<>(candidates);
		result.sort(Comparator.comparingInt(Candidate::getStorePriority).reversed());
		if (requestedId != null && !requestedId.isEmpty()) {
			Candidate requested = result.stream().filter(c -> requestedId.equals(c.getId()))
					.findFirst().orElse(null);
			if (requested == null) {
				log.warn("Requested instance store \"{}\" is not available", requestedId);
			}
			else {
				result.remove(requested);
				result.add(0, requested);
			}
		}
		return result;
	}

	/**
	 * Create a store from the first candidate that succeeds.
	 *
	 * @param candidates the candidates
	 * @param requestedId the requested id, may be <code>null</code>
	 * @param dataSet the data set
	 * @param directory the store directory
	 * @param services the service provider, may be <code>null</code>
	 * @return the created store
	 * @throws IOException if no store could be created
	 */
	static InstanceStore createStore(List<? extends Candidate> candidates, String requestedId,
			DataSet dataSet, Path directory, ServiceProvider services) throws IOException {
		List<Candidate> ordered = order(candidates, requestedId);
		if (ordered.isEmpty()) {
			throw new IOException("No instance store implementation available");
		}
		IOException failure = new IOException("No instance store could be created");
		for (Candidate candidate : ordered) {
			try {
				InstanceStore store = candidate.createFactory().create(dataSet, directory,
						services);
				log.debug("Using instance store \"{}\" for {} data", candidate.getId(), dataSet);
				return store;
			} catch (Exception e) {
				log.error("Failed to create instance store \"" + candidate.getId() + "\"", e);
				failure.addSuppressed(e);
			}
		}
		throw failure;
	}

}
