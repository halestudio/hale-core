/*
 * Copyright (c) 2018 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.common.core.report.util

import groovy.transform.CompileStatic

import java.util.concurrent.TimeUnit

import eu.esdihumboldt.hale.common.core.io.report.IOReport
import eu.esdihumboldt.hale.common.core.report.Report
import eu.esdihumboldt.hale.common.core.report.Statistics
import eu.esdihumboldt.util.groovy.collector.StatsCollector

/**
 * Report statistics helper.
 *
 * @author Simon Templer
 */
@CompileStatic
class StatisticsHelper {

	/**
	 * Get the statistics for a report.
	 * @param report the report
	 * @return the report statistics
	 */
	StatsCollector getStatistics(Report report) {
		StatsCollector stats = new StatsCollector()
		String taskType = report.getTaskType()
		if (taskType == null) {
			taskType = 'unknown task'
		}
		StatsCollector sub = stats.at(taskType);

		//XXX this prevents aggregation
		//		if (report instanceof IOReport) {
		//			def loc = report.target?.location
		//			sub = sub.at(loc.toString())
		//		}

		if (report instanceof Statistics) {
			// apply collected statistics
			StatsCollector collected = report.stats()
			def store = collected.saveToMapListStructure(false)
			sub.loadFromMapListStructure(store)
		}

		// add general report statistics
		StatsCollector rep = sub.at('report')
		rep.at('summary').set(report.summary)
		rep.at('completed').set(report.success)
		rep.at('errors').set(report.totalErrors)
		rep.at('warnings').set(report.totalWarnings)

		if (report.startTime && report.timestamp) {
			def ms = report.timestamp.time - report.startTime.time
			def seconds = TimeUnit.SECONDS.convert(ms, TimeUnit.MILLISECONDS)
			rep.at('duration').set(seconds)
		}

		if (report instanceof IOReport) {
			def loc = report.target?.location
			if (loc) {
				rep.at('location').set(loc.toString())
			}
		}

		stats
	}

	/**
	 * Get the combined statistics for the provided reports.
	 *
	 * @param reports the reports
	 */
	StatsCollector getStatistics(Iterable<Report<?>> reports, boolean mongoCompatible = false) {
		StatsCollector root = new StatsCollector()

		// get aggregated stats from reports
		StatsCollector reps = root.at('aggregated')
		Map repsMap = new HashMap()
		reports.each { Report rep ->
			StatsCollector stats = getStatistics(rep)
			Map statMap = (Map) stats.saveToMapListStructure(false)
			if (statMap != null) {
				repsMap = new StatsMerge(mongoCompatible).mergeConfigs(repsMap, statMap)
			}
		}
		reps.loadFromMapListStructure(repsMap);

		//TODO also add individual report information?

		return root
	}
}
