/*
 * Copyright (c) 2013 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.util.groovy.builder

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode

import org.codehaus.groovy.runtime.InvokerHelper



/**
 * Builder base class that supports creating nodes dependent on the parent node.
 *
 * @author Simon Templer
 */
@CompileStatic
abstract class BuilderBase {

	/**
	 * The current node.
	 */
	def current

	private final boolean cloneClosures;

	public BuilderBase(boolean cloneClosures = true) {
		this.cloneClosures = cloneClosures;
	}

	/**
	 * Reset the builder
	 */
	void reset() {
		current = null
	}

	/**
	 * Called on for any missing method.
	 *
	 * @param name the method name
	 * @param args the arguments
	 * @return something
	 */
	@CompileStatic(TypeCheckingMode.SKIP)
	@TypeChecked
	def methodMissing(String name, def args) {
		List list = InvokerHelper.asList(args)

		// determine named parameters (must be first)
		Map attributes = null
		def start = 0
		if (list && list[0] instanceof Map) {
			attributes = (Map) list[0]
			start = 1
		}

		// determine closure (must be last)
		def end = list.size()
		Closure closure = null
		if (list && list.last() instanceof Closure) {
			if (cloneClosures)
				closure = (Closure) list.last().clone()
			else
				closure = list.last()
			closure.delegate = this
			end--
		}

		// determine other parameters
		List params = null
		if (start < end) {
			params = list.subList(start, end)
		}

		def parent = current
		def node = internalCreateNode(name, attributes, params, parent, closure != null)
		current = node

		closure?.call()
		internalExtendNode(node)

		current = parent

		internalNodeWrapup(node)

		// return the node created by the call
		internalExtractNode(node)
	}

	/**
	 * Create a new node.
	 * @param name the new node name
	 * @param args the arguments
	 * @return the created node
	 */
	def createNode(String name, def args) {
		methodMissing(name, args)
	}

	/**
	 * Create a new node.
	 *
	 * @param name the node name
	 * @param attributes the named parameters, may be <code>null</code>
	 * @param params other parameters, may be <code>null</code>
	 * @param parent the parent node, may be <code>null</code>
	 * @param subClosure states if there is a sub-closure for this node
	 * @return the created node
	 */
	protected def abstract internalCreateNode(String name, Map attributes, List params, def parent, boolean subClosure);

	/**
	 * Method that is called for a node created with
	 * {@link #internalCreateNode(String, Map, List, Object, boolean)} after all
	 * sub-closures have been handled.
	 *
	 * @param node the created node
	 */
	protected void internalNodeWrapup(def node) {
		// override me
	}

	/**
	 * Method that is called after (or if it does not exist: instead) a node's
	 * sub-closure. Thus builder calls to extend the node can be performed here.
	 *
	 * @param node the created node to extend
	 */
	protected void internalExtendNode(def node) {
		// override me
	}

	/**
	 * Method that is called at the end of {@link #createNode(String, def)} to
	 * extract a value to return from a node. This return value is only relevant
	 * for use inside the builder closure. The default implementation just
	 * returns the node itself unchanged.
	 *
	 * @param node the node to extract a return value from
	 * @return the return value for the given node
	 */
	protected def internalExtractNode(def node) {
		node
	}

	public boolean isCloneClosures() {
		return cloneClosures;
	}
}
