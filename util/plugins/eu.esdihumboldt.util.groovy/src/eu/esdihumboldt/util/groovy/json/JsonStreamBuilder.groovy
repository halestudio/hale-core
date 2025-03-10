/*
 * Copyright (c) 2014 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.util.groovy.json

import groovy.json.JsonOutput
import groovy.json.StringEscapeUtils
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import eu.esdihumboldt.util.groovy.builder.BuilderBase


/**
 * Alternative to {@link StreamingJsonBuilder} with a slightly different syntax
 * and support concerning arrays. It is for one time use only, as it is bound to
 * a {@link Writer} instance.<br>
 * <br>
 * Use {@link JsonStreamBuilder} to create JSON objects and arrays of JSON objects,
 * for simpler structures use for instance {@link JsonOutput}.
 * <br>
 * See {@link JsonStreamBuilderTest} for examples.
 *
 * @author Simon Templer
 */
@CompileStatic
class JsonStreamBuilder extends BuilderBase {

	private static final String INDENT = '\t'

	// represents a builder node
	private static class NodeState {
		// the indentation level
		int level = 0
		// the node name
		String name
		// if the node is part of an array
		boolean array = false
		// if the node represents a JSON object
		boolean object = false
		// if the node represents a virtual root
		boolean root = false

		// the last child of the node
		NodeState lastChild
	}

	final Writer writer

	final boolean prettyPrint

	final boolean rootArray

	/**
	 * If set to true the next created node will create an array (w/o the need
	 * to include <code>[]</code> in the node name).
	 */
	boolean startArray = false

	/**
	 * Create a new builder streaming JSON to the given writer.
	 *
	 * @param writer the writer, it's the callers responsibility to close the writer
	 * @param prettyPrint if the output should be pretty printed
	 * @param rootArray if as root an array should be created instead of a root object
	 */
	JsonStreamBuilder(Writer writer, boolean prettyPrint = false, boolean rootArray = false) {
		this.writer = writer
		this.prettyPrint = prettyPrint
		this.rootArray = rootArray
	}

	/**
	 * Creates a property with the given content in the current object. If no
	 * root JSON object exists yet it will be created and wrap the property.<br>
	 * <br>
	 * Convenience method for type safe JSON building.
	 *
	 * @param name the property name
	 * @param array states if the property should be an array
	 * @param content the property content
	 */
	void call(String name, boolean array = false, Object content) {
		startArray = array
		createNode(name, [content])
	}

	/**
	 * Creates a property in the current object that contains a child object with
	 * the given properties. If no root JSON object exists yet it will be
	 * created and wrap the property.<br>
	 * <br>
	 * Convenience method for type safe JSON building.
	 *
	 * @param name the property name
	 * @param array states if the property should be an array
	 * @param properties the properties of the child object
	 */
	void call(String name, boolean array = false, Map properties) {
		startArray = array
		createNode(name, [properties])
	}

	/**
	 * Creates a property in the current object that contains a child object
	 * defined by the given closure. If no root JSON object exists yet it will be
	 * created and wrap the property.<br>
	 * <br>
	 * Convenience method for type safe JSON building.
	 *
	 * @param name the property name
	 * @param array states if the property should be an array
	 * @param closure the closure defining the child object
	 */
	void call(String name, boolean array = false, Closure closure) {
		startArray = array
		createNode(name, [closure])
	}

	/**
	 * Creates a property in the current object that contains a child object with
	 * the given properties and additional content defined through the given closure.
	 * If no root JSON object exists yet it will be created and wrap the property.<br>
	 * <br>
	 * Convenience method for type safe JSON building.
	 *
	 * @param name the property name
	 * @param array states if the property should be an array
	 * @param properties the properties of the child object
	 * @param closure the closure defining additional content of the child object
	 */
	void call(String name, boolean array = false, Map properties, Closure closure) {
		startArray = array
		createNode(name, [properties, closure])
	}

	/**
	 * Creates a JSON root object. If a parent already exists will just call
	 * the given closure.
	 *
	 * @param closure the closure defining the object
	 */
	public void call(Closure closure) {
		def parent = current
		boolean rootArrayChild = false
		if (parent == null) {
			if (rootArray) {
				// root node that is an array
				current = new NodeState(root: true)
				writer.write( '[' )
			}
			else {
				// non-virtual root node
				current = new NodeState(object: true, root: false)
				writer.write( '{' )
			}
		}
		else if (rootArray) {
			NodeState parentNode = (NodeState) parent
			if (parentNode.root) {
				// child object of a root array
				def previous = parentNode.lastChild

				// need a comma?
				if (previous != null) {
					writer << ','
				}

				def currentNode = new NodeState(object: true, array: true, root: false, level: 1)
				if (prettyPrint) {
					writer << '\n'
					currentNode.level.times { writer << INDENT }
				}

				parentNode.lastChild = currentNode
				current = currentNode
				writer.write( '{' )
				rootArrayChild = true
			}
		}

		closure = (Closure) closure.clone()
		closure.delegate = this
		closure.call()

		if (parent == null || rootArrayChild) {
			internalNodeWrapup(current)
			current = parent
			if (parent == null) {
				// only reset after the root
				reset()
			}
		}
	}

	@Override
	protected Object internalCreateNode(String name, Map attributes, List params, Object parent,
			boolean subClosure) {
		NodeState parentNode = (NodeState) parent
		NodeState node = new NodeState()
		NodeState previous = null
		if (parentNode) {
			if (parentNode.root && rootArray) {
				throw new IllegalStateException('Named nodes not allowed in root array')
			}

			// has a parent
			previous = parentNode.lastChild
			parentNode.lastChild = node
			node.level = parentNode.level + 1
		}
		else {
			// has no parent -> create a root object
			node.root = true
			node.level = 1
			writer << '{'
		}

		// name denotes an array?
		if (name.endsWith('[]')) {
			name = name[0..name.length()-3]
			node.array = true
		}
		// builder configured to start an array?
		else if (startArray) {
			node.array = true
		}

		// same name as previous node?
		if (previous != null && previous.name == name) {
			if (previous.array) {
				// a continued array, even if not marked with []
				node.array = true
			}
			else {
				// continued node, but was not created as array -> invalid JSON
				throw new IllegalStateException("Multiple subsequent nodes with the same name ('$name'), but the first one was not created as array")
			}
		}

		// reset startArray property
		startArray = false

		// store child name for later reference
		node.name = name

		// closes a previous array?
		if (previous?.array && previous.name != name) {
			if (prettyPrint) {
				writer << '\n'
				(previous.level - 1).times { writer << INDENT }
			}
			writer << ']'
		}

		// need a comma?
		if (previous != null) {
			writer << ','
		}

		if (prettyPrint) {
			writer << '\n'
			node.level.times { writer << INDENT }
		}

		// a named node
		// write name if not a continued array
		if (previous == null || !(previous.array && node.array && previous.name == node.name)) {
			writer << "\"${StringEscapeUtils.escapeJava(name)}\""
			writer << ':'
			if (prettyPrint) {
				writer << ' '
			}
		}

		// is an array?
		if (node.array) {
			node.level++ // increase indent level due to array

			// starts an array?
			if (previous == null || previous.name != name) {
				writer << '['
				if (prettyPrint) {
					writer << '\n'
					node.level.times { writer << INDENT }
				}
			}
			else {
				if (prettyPrint) {
					// write the additional indent for the array
					// (as previously the node level was not yet increased)
					writer << INDENT
				}
			}
		}

		if (subClosure) {
			// JSON object
			internalWriteStartJsonObject(node, attributes)
		}
		else {
			if (params) {
				// JSON value

				// there should be no named arguments
				if (attributes) {
					throw new IllegalStateException('Not allowed to provide both value and named parameters')
				}

				if (params.size() == 1) {
					// single object as value
					writer << internalToJson(params[0])
				} else {
					// list as value
					writer << internalToJson(params)
				}
			}
			else {
				// JSON object w/ or w/o additional attributes
				internalWriteStartJsonObject(node, attributes)
			}
		}

		node
	}

	private def internalWriteStartJsonObject(NodeState node, Map attributes) {
		// JSON object with or w/o key:value
		writer << '{'
		node.object = true;

		if (attributes) {
			// write attributes
			attributes.eachWithIndex { def key, def value, int index ->
				if (index > 0) {
					writer << ','
				}
				if (prettyPrint) {
					writer << '\n'
					(node.level + 1).times { writer << INDENT }
				}
				writer << internalToJson(key)
				writer << ':'
				if (prettyPrint) {
					writer << ' '
				}
				writer << internalToJson(value)
			}

			if (prettyPrint) {
				writer << '\n'
			}

			// mark node to already have children (so the comma is written appropriately)
			node.lastChild = new NodeState()
		}
	}

	@CompileStatic(TypeCheckingMode.SKIP)
	private static def internalToJson(object) {
		if (object instanceof RawJson) {
			object.json
		}
		else {
			// not compiled static to allow method selection based on type
			JsonOutput.toJson(object)
		}
	}

	@Override
	protected void internalNodeWrapup(Object node) {
		if (node == null) {
			return
		}

		NodeState state = (NodeState) node

		// close an array (if the last child is in an array)
		if (state.lastChild != null && state.lastChild.array && !(state.root && rootArray)) {
			if (prettyPrint) {
				writer << '\n'
				(state.lastChild.level - 1).times { writer << INDENT }
			}
			writer << ']'
		}

		// close an object (if the node is an object)
		if (state.object) {
			if (prettyPrint) {
				writer << '\n'
				state.level.times { writer << INDENT }
			}
			writer << '}'
		}

		// close root (if the node is a root node not created by #call(Closure) or a root array)
		if (state.root) {
			if (prettyPrint) {
				writer << '\n'
			}
			if (rootArray) {
				writer << ']'
			}
			else {
				writer << '}'
			}
		}

		// remove child reference (allow clean-up)
		state.lastChild = null
	}

	@Override
	protected Object internalExtractNode(Object node) {
		// don't expose nodes!
		// return null instead
		null
	}
}
