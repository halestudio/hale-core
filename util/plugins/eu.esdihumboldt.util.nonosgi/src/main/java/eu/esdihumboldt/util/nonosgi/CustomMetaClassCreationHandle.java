package eu.esdihumboldt.util.nonosgi;

import java.lang.reflect.Constructor;
import java.util.stream.Collectors;

import org.eclipse.equinox.nonosgi.registry.RegistryFactoryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.esdihumboldt.util.groovy.meta.extension.MetaClassDescriptor;
import eu.esdihumboldt.util.groovy.meta.extension.MetaClassExtension;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import groovy.lang.MetaClassRegistry.MetaClassCreationHandle;

/**
 * Adapts created meta classes with delegating meta classes registered in the
 * {@link MetaClassExtension}.
 *
 * @author Simon Templer
 */
public class CustomMetaClassCreationHandle extends MetaClassCreationHandle {

	private static final Logger log = LoggerFactory.getLogger(CustomMetaClassCreationHandle.class);

	private final MetaClassExtension ext;

	public CustomMetaClassCreationHandle() {
		// initialize registry
		RegistryFactoryHelper.getRegistry();

		ext = new MetaClassExtension();

		if (log.isDebugEnabled()) {

			var elements = ext.getElements();
			if (elements.isEmpty()) {
				log.debug("No registered meta class descriptors");
			}
			else {
				var list = elements.stream()
						.map(d -> d.getMetaClass() + " applied to " + d.getForClass())
						.collect(Collectors.joining("\n    "));
				log.debug("Registered meta class descriptors:\n    " + list);
			}
		}
	}

	@Override
	protected MetaClass createNormalMetaClass(@SuppressWarnings("rawtypes") Class theClass,
			MetaClassRegistry registry) {
		MetaClass metaClass = super.createNormalMetaClass(theClass, registry);

		for (MetaClassDescriptor descriptor : ext.getElements()) {
			if (descriptorApplies(descriptor, theClass)) {
				// create meta class
				Class<?> delegatingMetaClass = descriptor.getMetaClass();
				try {
					Constructor<?> constructor = delegatingMetaClass
							.getConstructor(MetaClass.class);
					metaClass = (MetaClass) constructor.newInstance(metaClass);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return metaClass;
	}

	/**
	 * Check if a meta class descriptor applies to a given class.
	 *
	 * @param descriptor the meta class descriptor
	 * @param theClass the class for which should be determined if the descriptor
	 *            applies
	 * @return <code>true</code> if the descriptor applies to the class,
	 *         <code>false</code> otherwise
	 */
	private boolean descriptorApplies(MetaClassDescriptor descriptor,
			@SuppressWarnings("rawtypes") Class theClass) {
		Class<?> forClass = descriptor.getForClass();
		if (descriptor.isForArray()) {
			if (theClass.isArray()) {
				Class<?> componentClass = theClass.getComponentType();
				if (componentClass != null) {
					return forClass.equals(componentClass)
							|| forClass.isAssignableFrom(componentClass);
				}
				else {
					// should actually not happen
					return false;
				}
			}
			else {
				// no array
				return false;
			}
		}
		else {
			return forClass.equals(theClass) || forClass.isAssignableFrom(theClass);
		}
	}

}
