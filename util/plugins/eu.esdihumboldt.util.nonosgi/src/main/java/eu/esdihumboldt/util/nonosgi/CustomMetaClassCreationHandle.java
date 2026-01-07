/*
 * Copyright (c) 2024 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
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

//		if (log.isDebugEnabled()) {

		var elements = ext.getElements();
		if (elements.isEmpty()) {
			log.debug("No registered meta class descriptors");
			System.out.println("###### No registered meta class descriptors");
		}
		else {
			var list = elements.stream()
					.map(d -> d.getMetaClass() + " applied to " + d.getForClass())
					.collect(Collectors.joining("\n    "));
			log.debug("Registered meta class descriptors:\n    " + list);
			System.out.println("Registered meta class descriptors:\n    " + list);
		}
//		}
	}

	@Override
	protected MetaClass createNormalMetaClass(@SuppressWarnings("rawtypes") Class theClass,
			MetaClassRegistry registry) {
		MetaClass metaClass = super.createNormalMetaClass(theClass, registry);
		log.info(
				"+++++++++ NONOsgi: CustomMetaClassCreationHandle: checking for meta class extensions for "
						+ theClass.getName());
//		System.out.println(
//				"+++++++++ NONOsgi: CustomMetaClassCreationHandle: checking for meta class extensions for "
//						+ theClass.getName());
		for (MetaClassDescriptor descriptor : ext.getElements()) {
//			log.info("+++++++++ NONOsgi: CustomMetaClassCreationHandle: checking descriptor for "
//					+ descriptor.getForClass().getName() + (descriptor.isForArray() ? "[]" : ""));
			if (descriptorApplies(descriptor, theClass)) {
				// create meta class
				Class<?> delegatingMetaClass = descriptor.getMetaClass();
				try {
					Constructor<?> constructor = delegatingMetaClass
							.getConstructor(MetaClass.class);
					metaClass = (MetaClass) constructor.newInstance(metaClass);
				} catch (Exception e) {
					log.error("Error creating meta class " + delegatingMetaClass.getName()
							+ " for class " + theClass.getName(), e);
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
		boolean result = false;
//		log.info("+++++++++ NONOsgi: CustomMetaClassCreationHandle: descriptorApplies: forClass="
//				+ forClass.getName() + ", theClass=" + theClass.getName());
//		System.out.println(
//				"+++++++++ NONOsgi: CustomMetaClassCreationHandle: descriptorApplies: forClass="
//						+ forClass.getName() + ", theClass=" + theClass.getName());
		if (descriptor.isForArray()) {
			if (theClass.isArray()) {
				Class<?> componentClass = theClass.getComponentType();
				if (componentClass != null) {
					result = forClass.equals(componentClass)
							|| forClass.isAssignableFrom(componentClass);
					if (theClass.getName().contains("String") && result) {
						System.out.println(
								"+++++++++ NONOsgi: CustomMetaClassCreationHandle: descriptorApplies: MATCHED"
										+ "metaclass name: " + descriptor.getMetaClass().getName()
										+ " forClass=" + forClass.getName() + ", theClass="
										+ theClass.getName());
					}
				}
				else {
					// should actually not happen
//					System.out.println(
//							"+++++++++ NONOsgi: should actually not happen, componentClass is null for array class!!"
//									+ theClass.getName());
					result = false;
				}
			}
			else {
				// no array
				result = false;
			}
		}
		else {
			result = forClass.equals(theClass) || forClass.isAssignableFrom(theClass);
			if (theClass.getName().contains("String")) {
				if (result) {
					System.out.println(
							"+++++++++ NONOsgi: CustomMetaClassCreationHandle: descriptorApplies: MATCHED for non-array check"
									+ " Current Thread: " + Thread.currentThread().getName()
									+ "metaclass name=" + descriptor.getMetaClass().getName()
									+ " forClass=" + forClass.getName() + ", theClass="
									+ theClass.getName() + ", result=" + result);
				}
				else {

					System.out.println(
							"+++++++++ NONOsgi: CustomMetaClassCreationHandle: descriptorApplies: NON-MATCHED for non-array check"
									+ " Current Thread: " + Thread.currentThread().getName()
									+ " metaclass name=" + descriptor.getMetaClass().getName()
									+ " forClass=" + forClass.getName() + ", theClass="
									+ theClass.getName() + ", result=" + result);
//				result = true;
//				System.out.println("+++++++++ NONOsgi: Forcing match for testing purposes"
//						+ "metaclass name: " + descriptor.getMetaClass().getName() + " forClass="
//						+ forClass.getName() + ", theClass=" + theClass.getName() + ", result="
//						+ result);
				}
			}
		}
		return result;
	}

}
