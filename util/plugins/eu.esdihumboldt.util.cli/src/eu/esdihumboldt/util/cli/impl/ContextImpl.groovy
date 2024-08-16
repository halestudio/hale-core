package eu.esdihumboldt.util.cli.impl

import groovy.transform.Immutable

import eu.esdihumboldt.util.cli.CommandContext

@Immutable
class ContextImpl implements CommandContext {

	String baseCommand
	String commandName
}
