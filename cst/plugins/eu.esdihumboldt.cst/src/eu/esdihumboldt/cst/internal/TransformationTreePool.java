/*
 * Copyright (c) 2012 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.cst.internal;

import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import eu.esdihumboldt.hale.common.align.model.Alignment;
import eu.esdihumboldt.hale.common.align.model.Cell;
import eu.esdihumboldt.hale.common.align.model.transformation.tree.TransformationTree;
import eu.esdihumboldt.hale.common.align.model.transformation.tree.context.ContextMatcher;
import eu.esdihumboldt.hale.common.align.model.transformation.tree.impl.TransformationTreeImpl;
import eu.esdihumboldt.hale.common.align.model.transformation.tree.visitor.ResetVisitor;

/**
 * Pool for transformation trees.
 *
 * @author Simon Templer
 */
public class TransformationTreePool {

	private final Alignment alignment;

	private final ListMultimap<Cell, TransformationTree> trees;

	private final ResetVisitor resetVisitor = new ResetVisitor();

	private final ContextMatcher matcher;

	/**
	 * Create a transformation tree pool.
	 *
	 * @param alignment the associated alignment
	 * @param matcher the context matcher to apply to a created tree
	 */
	public TransformationTreePool(Alignment alignment, ContextMatcher matcher) {
		this.alignment = alignment;
		this.matcher = matcher;

		trees = ArrayListMultimap.create();
	}

	/**
	 * Get a transformation tree from the pool.
	 *
	 * @param typeCell the type cell for the transformation tree
	 * @return the transformation tree
	 */
	public TransformationTree getTree(Cell typeCell) {
		synchronized (trees) {
			List<TransformationTree> treeList = trees.get(typeCell);
			if (treeList.isEmpty()) {
				TransformationTree tree = new TransformationTreeImpl(alignment, typeCell);
				if (matcher != null) {
					matcher.findMatches(tree);
				}
				return tree;
			}
			else {
				TransformationTree tree = treeList.remove(0);
				return tree;
			}
		}
	}

	/**
	 * Release a tree to the pool.
	 *
	 * @param tree the transformation tree that is no longer needed
	 */
	public void releaseTree(TransformationTree tree) {
		tree.accept(resetVisitor); // remove all annotations
		synchronized (trees) {
			trees.put(tree.getTypeCell(), tree);
		}
	}

}
