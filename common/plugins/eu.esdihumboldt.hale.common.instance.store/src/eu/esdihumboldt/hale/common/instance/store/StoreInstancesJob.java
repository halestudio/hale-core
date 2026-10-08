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

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.namespace.QName;

import org.eclipse.collections.api.block.procedure.primitive.ObjectIntProcedure;
import org.eclipse.collections.api.factory.primitive.ObjectIntMaps;
import org.eclipse.collections.api.map.primitive.MutableObjectIntMap;
import org.eclipse.collections.api.map.primitive.ObjectIntMap;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.core.report.LogAware;
import eu.esdihumboldt.hale.common.core.report.Message;
import eu.esdihumboldt.hale.common.core.report.ReportHandler;
import eu.esdihumboldt.hale.common.core.report.ReportSimpleLogSupport;
import eu.esdihumboldt.hale.common.core.report.Reporter;
import eu.esdihumboldt.hale.common.core.report.SimpleLogContext;
import eu.esdihumboldt.hale.common.core.report.impl.DefaultReporter;
import eu.esdihumboldt.hale.common.core.report.impl.MessageImpl;
import eu.esdihumboldt.hale.common.core.service.ServiceProvider;
import eu.esdihumboldt.hale.common.instance.index.InstanceIndexService;
import eu.esdihumboldt.hale.common.instance.model.Identifiable;
import eu.esdihumboldt.hale.common.instance.model.IdentifiableInstanceReference;
import eu.esdihumboldt.hale.common.instance.model.Instance;
import eu.esdihumboldt.hale.common.instance.model.InstanceCollection;
import eu.esdihumboldt.hale.common.instance.model.InstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResolvableInstanceReference;
import eu.esdihumboldt.hale.common.instance.model.ResourceIterator;
import eu.esdihumboldt.hale.common.instance.processing.InstanceProcessingExtension;
import eu.esdihumboldt.hale.common.instance.processing.InstanceProcessor;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.TypeIndex;

/**
 * Job storing instances in an {@link InstanceStore}, feeding instance
 * processors and the instance index.
 */
public class StoreInstancesJob extends Job {

	/**
	 * Report task type.
	 */
	public static final String TASK_TYPE = "eu.esdihumboldt.hale.instance.store.load";

	private static final ALogger log = ALoggerFactory.getLogger(StoreInstancesJob.class);

	private static class DefaultLog extends DefaultReporter<Message>
			implements ReportSimpleLogSupport<Message> {

		DefaultLog(String taskName) {
			super(taskName, TASK_TYPE, Message.class, false);
		}

		@Override
		public Message createMessage(String message, Throwable e) {
			return new MessageImpl(message, e);
		}
	}

	private final InstanceStore store;
	private InstanceCollection instances;
	private final TypeIndex types;
	private final ServiceProvider serviceProvider;
	private final ReportHandler reportHandler;
	private final boolean doProcessing;
	private final DefaultLog report;

	/**
	 * @param name the job name
	 * @param store the store to add the instances to
	 * @param instances the instances to store
	 * @param types the type index to resolve nested types when instances are
	 *            resolved by reference, may be <code>null</code>
	 * @param serviceProvider the service provider, required if
	 *            <code>doProcessing</code> is set
	 * @param reportHandler the report handler, may be <code>null</code>
	 * @param doProcessing if instance processors and the instance index should be
	 *            fed with the stored instances
	 */
	public StoreInstancesJob(String name, InstanceStore store, InstanceCollection instances,
			TypeIndex types, ServiceProvider serviceProvider, ReportHandler reportHandler,
			boolean doProcessing) {
		super(name);
		setUser(true);
		this.store = store;
		this.instances = instances;
		this.types = types;
		this.serviceProvider = serviceProvider;
		this.reportHandler = reportHandler;
		this.doProcessing = doProcessing;
		this.report = reportHandler != null ? new DefaultLog("Load data into temporary database")
				: null;
	}

	@Override
	public IStatus run(IProgressMonitor monitor) {
		boolean exactProgress = instances.hasSize();
		int size = instances.size();
		monitor.beginTask("Store instances in temporary database",
				exactProgress ? size : IProgressMonitor.UNKNOWN);

		AtomicInteger count = new AtomicInteger();
		MutableObjectIntMap<QName> typeCount = ObjectIntMaps.mutable.empty();
		if (report != null) {
			report.setStartTime(new Date());
		}

		final List<InstanceProcessor> processors = doProcessing
				? new InstanceProcessingExtension(serviceProvider).getInstanceProcessors()
				: Collections.emptyList();
		final InstanceIndexService indexService = doProcessing
				? serviceProvider.getService(InstanceIndexService.class)
				: null;
		final InstanceCollection resolver = store.getInstances(types);

		try {
			InstanceStoreWriter writer = store.openWriter();
			try {
				SimpleLogContext.withLog(report, () -> {
					if (report != null && instances instanceof LogAware) {
						((LogAware) instances).setLog(report);
					}
					long lastUpdate = 0;
					try (ResourceIterator<Instance> it = instances.iterator()) {
						while (it.hasNext() && !monitor.isCanceled()) {
							Instance instance = it.next();
							processInstance(instance);

							InstanceReference ref;
							try {
								ref = writer.add(instance);
							} catch (IllegalArgumentException e) {
								// instance cannot be stored - skip it
								String msg = "Instance could not be stored in the temporary database and is skipped";
								if (report != null) {
									report.error(new MessageImpl(msg, e));
								}
								else {
									log.error(msg, e);
								}
								continue;
							}
							ResolvableInstanceReference resolvable = new ResolvableInstanceReference(
									new IdentifiableInstanceReference(ref, Identifiable.getId(ref)),
									resolver);
							processors.forEach(p -> p.process(instance, resolvable));
							if (indexService != null) {
								indexService.add(instance, resolvable);
							}

							count.incrementAndGet();
							TypeDefinition type = instance.getDefinition();
							if (type != null) {
								typeCount.addToValue(type.getName(), 1);
							}
							if (exactProgress) {
								monitor.worked(1);
							}
							long now = System.currentTimeMillis();
							if (now - lastUpdate > 100) {
								monitor.subTask(MessageFormat.format("{0}{1} instances processed",
										String.valueOf(count.get()),
										size != InstanceCollection.UNKNOWN_SIZE ? "/" + size : ""));
								lastUpdate = now;
							}
						}
					} finally {
						if (report != null && instances instanceof LogAware) {
							((LogAware) instances).setLog(null);
						}
					}
				});
			} finally {
				writer.close();
			}
		} catch (Exception e) {
			String message = "Error storing instances in temporary database";
			if (report != null) {
				reportTypeCount(report, typeCount);
				report.error(new MessageImpl(message, e));
				report.setSuccess(false);
				reportHandler.publishReport(report);
			}
			log.error(message, e);
			monitor.done();
			return new Status(IStatus.ERROR, "eu.esdihumboldt.hale.common.instance.store", message,
					e);
		} finally {
			instances = null;
		}

		try {
			onComplete();
		} catch (RuntimeException e) {
			String message = "Error while post processing stored instances";
			if (report != null) {
				report.error(new MessageImpl(message, e));
			}
			else {
				log.error(message, e);
			}
		}

		String message = MessageFormat.format("Stored {0} instances in the temporary database.",
				count);
		if (monitor.isCanceled()) {
			String warn = "Loading instances was canceled, incomplete data set in the temporary database.";
			if (report != null) {
				report.warn(new MessageImpl(warn, null));
			}
			else {
				log.warn(warn);
			}
		}
		if (report != null) {
			reportTypeCount(report, typeCount);
			report.setSuccess(true);
			report.setSummary(message);
			reportHandler.publishReport(report);
		}
		else {
			log.info(message);
		}

		monitor.done();
		return new Status(monitor.isCanceled() ? IStatus.CANCEL : IStatus.OK,
				"eu.esdihumboldt.hale.common.instance.store", message);
	}

	private void reportTypeCount(Reporter<Message> report, ObjectIntMap<QName> typeCount) {
		typeCount.forEachKeyValue((ObjectIntProcedure<QName>) (typeName, count) -> {
			StringBuilder msg = new StringBuilder("Stored ");
			msg.append(count).append(" instances of type ").append(typeName.getLocalPart());
			String ns = typeName.getNamespaceURI();
			if (ns != null && !ns.isEmpty()) {
				msg.append(" (").append(ns).append(")");
			}
			report.info(new MessageImpl(msg.toString(), null));
			report.stats().at("countPerType").at(typeName.toString()).set(count);
		});
	}

	/**
	 * Called for each instance before it is stored.
	 *
	 * @param instance the instance
	 */
	protected void processInstance(Instance instance) {
		// override me
	}

	/**
	 * Called after all instances were stored (also if the job was canceled).
	 */
	protected void onComplete() {
		// override me
	}

}
