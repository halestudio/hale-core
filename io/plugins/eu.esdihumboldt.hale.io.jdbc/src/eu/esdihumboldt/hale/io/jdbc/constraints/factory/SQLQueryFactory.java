
/*
 * Copyright (c) 2017 wetransform GmbH
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.esdihumboldt.hale.io.jdbc.constraints.factory;

import eu.esdihumboldt.hale.common.core.io.Text;
import eu.esdihumboldt.hale.common.core.io.Value;
import eu.esdihumboldt.hale.common.schema.model.Definition;
import eu.esdihumboldt.hale.common.schema.model.constraint.factory.ClassResolver;
import eu.esdihumboldt.hale.common.schema.model.constraint.factory.TypeReferenceBuilder;
import eu.esdihumboldt.hale.common.schema.model.constraint.factory.TypeResolver;
import eu.esdihumboldt.hale.common.schema.model.constraint.factory.ValueConstraintFactory;
import eu.esdihumboldt.hale.io.jdbc.constraints.SQLQuery;

/**
 * Value conversion for {@link SQLQuery} constraint.
 *
 * @author Simon Templer
 */
public class SQLQueryFactory implements ValueConstraintFactory<SQLQuery> {

	@Override
	public Value store(SQLQuery constraint, TypeReferenceBuilder typeIndex) throws Exception {
		String query = constraint.getQuery();
		if (query != null) {
			return Value.of(new Text(query));
		}
		else {
			return null;
		}
	}

	@Override
	public SQLQuery restore(Value value, Definition<?> definition, TypeResolver typeIndex,
			ClassResolver resolver) throws Exception {
		String query;
		Text text = value.as(Text.class);
		if (text != null) {
			query = text.getText();
		}
		else {
			query = value.as(String.class);
		}

		if (query != null) {
			return new SQLQuery(query);
		}
		return SQLQuery.NO_QUERY;
	}

}
