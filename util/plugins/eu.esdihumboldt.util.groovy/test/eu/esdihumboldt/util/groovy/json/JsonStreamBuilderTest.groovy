/*
 * Copyright (c) 2003-2012 wetransform GmbH
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

import groovy.transform.CompileStatic

import org.junit.Test

/**
 * Tests for {@link JsonStreamBuilder}.
 *
 * @author Tim Yates
 * @author Guillaume Laforge
 * @author Simon Templer
 */
class JsonStreamBuilderTest {

	@Test
	void testJsonBuilderConstructor() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder(w)
			json {
				a 1
				b true
			}
			assert w.toString() == '{"a":1,"b":true}'
		}
	}

	@Test
	void testJsonBuilderRawJson() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder(w)
			json {
				a 1
				b new RawJson('{"c":2,"d":false}')
			}
			assert w.toString() == '{"a":1,"b":{"c":2,"d":false}}'
		}
	}

	@Test
	@CompileStatic
	void testJsonBuilderTypeSafe() {
		new StringWriter().with { Writer w ->
			def json = new JsonStreamBuilder(w)
			json {
				json 'a', 1
				json 'b', true
			}
			assert w.toString() == '{"a":1,"b":true}'
		}
	}

	@Test
	void testJsonBuilderPrettyPrint() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder(w, true)
			json {
				a 1
				b true
			}
			assert w.toString() == '''{
\t"a": 1,
\t"b": true
}'''
		}
	}

	@Test(expected = Throwable)
	void testJsonBuilderSameNameFail() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder(w)
			json {
				a 1
				a true
			}
		}
	}

	@Test(expected = Throwable)
	void testJsonBuilderSameNameFail2() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder(w)
			json {
				a 1
				'a[]' true
			}
		}
	}

	@Test
	void testVirtualRootPrettyPrint() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder(w, true)
			json.x {
				a 1
				b true
			}
			assert w.toString() == '''{
\t"x": {
\t\t"a": 1,
\t\t"b": true
\t}
}'''
		}
	}

	@Test
	void testEmptyObject() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )
			json {
			}

			assert w.toString() == '{}'
		}
	}

	@Test
	void testRootArrayObjects() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w, false, true)
			json {
				json { a 1 }
				json { b 2 }
			}

			assert w.toString() == '[{"a":1},{"b":2}]'
		}
	}

	@Test
	void testRootArrayEmpty() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w, false, true)
			json {
			}

			assert w.toString() == '[]'
		}
	}

	@Test
	void testRootArrayObjectsFail() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w, false, true)
			try {
				json { a 1 }
				json { b 2 }
			} catch (e) {
				return
			}

			fail()
		}
	}

	@Test
	void testRootArrayObjectNested() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w, false, true)
			json {
				json {
					json {
						a 1
					}
				}
				json {
					b 2
					c { d 3 }
				}
			}

			assert w.toString() == '[{"a":1},{"b":2,"c":{"d":3}}]'
		}
	}

	@Test
	void testRootArrayObjectNestedPrettyPrint() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w, true, true)
			json {
				json {
					json {
						a 1
					}
				}
				json {
					b 2
					c { d 3 }
				}
			}

			assert w.toString() == '''[
\t{
\t\t"a": 1
\t},
\t{
\t\t"b": 2,
\t\t"c": {
\t\t\t"d": 3
\t\t}
\t}
]'''
		}
	}

	@Test
	void testBasicObject() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )
			json {
				a 1
				b true
				c null
			}

			assert w.toString() == '{"a":1,"b":true,"c":null}'
		}
	}

	@Test
	void testNestedObjects() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )
			json {
				a {
					//
					b {
						//
						c 1 //
					} //
				} //
			}

			assert w.toString() == '{"a":{"b":{"c":1}}}'
		}
	}

	@Test
	void testNestedObjectsPrettyPrint() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w, true )
			json {
				a {
					//
					b {
						//
						c 1 //
					} //
				} //
			}

			assert w.toString() == '''{
\t"a": {
\t\t"b": {
\t\t\t"c": 1
\t\t}
\t}
}'''
		}
	}

	@Test
	void testLoopArray() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )

			json {
				for (i in 1..3) {
					'item[]' {
						id i
						name "name$i"
					}
				}
			}

			assert w.toString() == '{"item":[{"id":1,"name":"name1"},{"id":2,"name":"name2"},{"id":3,"name":"name3"}]}'
		}
	}

	@Test
	@CompileStatic
	void testLoopArrayTypeSafe() {
		new StringWriter().with { Writer w ->
			def json = new JsonStreamBuilder( w )

			json {
				for (i in 1..3) {
					json 'item', true, {
						json 'id', i
						json 'name', "name$i"
					}
				}
			}

			assert w.toString() == '{"item":[{"id":1,"name":"name1"},{"id":2,"name":"name2"},{"id":3,"name":"name3"}]}'
		}
	}

	@Test
	void testLoopStartArray() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )

			json {
				json.startArray = true // mark next call as array
				for (i in 1..3) {
					item {
						id i
						name "name$i"
					}
				}
			}

			assert w.toString() == '{"item":[{"id":1,"name":"name1"},{"id":2,"name":"name2"},{"id":3,"name":"name3"}]}'
		}
	}

	@Test
	void testLoopArrayPrettyPrint() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w, true )

			json {
				for (i in 1..3) {
					'item[]' {
						id i
						name "name$i"
					}
				}
			}

			assert w.toString() == '''{
\t"item": [
\t\t{
\t\t\t"id": 1,
\t\t\t"name": "name1"
\t\t},
\t\t{
\t\t\t"id": 2,
\t\t\t"name": "name2"
\t\t},
\t\t{
\t\t\t"id": 3,
\t\t\t"name": "name3"
\t\t}
\t]
}'''
		}
	}

	@Test
	void testNestedArrayPrettyPrint() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w, true )

			json {
				'item[]' {
					name 'item'
					for (i in 1..2) {
						'code[]' i*32
					}
				}
			}

			assert w.toString() == '''{
\t"item": [
\t\t{
\t\t\t"name": "item",
\t\t\t"code": [
\t\t\t\t32,
\t\t\t\t64
\t\t\t]
\t\t}
\t]
}'''
		}
	}

	@Test
	void testDoubleObject() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )

			json {
				for (i in 1..3) {
					'item[]' {
						json {
							// should have no effect
							id i
							name "name$i"
						}
					}
				}
			}

			assert w.toString() == '{"item":[{"id":1,"name":"name1"},{"id":2,"name":"name2"},{"id":3,"name":"name3"}]}'
		}
	}

	@Test
	void testDoubleObject2() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )

			json {
				for (i in 1..3) {
					json {
						// should have no effect
						'item[]' {
							id i
							name "name$i"
						}
					}
				}
			}

			assert w.toString() == '{"item":[{"id":1,"name":"name1"},{"id":2,"name":"name2"},{"id":3,"name":"name3"}]}'
		}
	}

	@Test
	void testStandardBuilderStyle() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )
			json.person {
				name "Guillaume"
				age 33
			}

			assert w.toString() == '{"person":{"name":"Guillaume","age":33}}'
		}
	}

	@Test
	void testMethodCallWithNamedArguments() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )
			json.person name: "Guillaume", age: 33

			assert w.toString() == '{"person":{"name":"Guillaume","age":33}}'
		}
	}

	@Test
	@CompileStatic
	void testMethodCallWithNamedArgumentsTypeSafe() {
		new StringWriter().with { Writer w ->
			def json = new JsonStreamBuilder( w )
			json 'person', [name: "Guillaume", age: 33]

			assert w.toString() == '{"person":{"name":"Guillaume","age":33}}'
		}
	}

	@Test
	void testElementHasListOfObjects() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )
			json.response {
				results 1, [a: 2]
			}

			assert w.toString() == '{"response":{"results":[1,{"a":2}]}}'
		}
	}

	@Test
	void testElementHasListOfObjects2() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )
			json.response {
				'results[]' 1
				'results[]' a: 2
			}

			assert w.toString() == '{"response":{"results":[1,{"a":2}]}}'
		}
	}

	@Test
	void testComplexStructureFromTheGuardian() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )
			json.response {
				status "ok"
				userTier "free"
				total 2413
				startIndex 1
				pageSize 10
				currentPage 1
				pages 242
				orderBy "newest"
				'results[]'(
						id: "world/video/2011/jan/19/tunisia-demonstrators-democracy-video",
						sectionId: "world",
						sectionName: "World news",
						webPublicationDate: "2011-01-19T15:12:46Z",
						webTitle: "Tunisian demonstrators demand new democracy - video",
						webUrl: "http://www.guardian.co.uk/world/video/2011/jan/19/tunisia-demonstrators-democracy-video",
						apiUrl: "http://content.guardianapis.com/world/video/2011/jan/19/tunisia-demonstrators-democracy-video"
						)
				'results[]' {
					id "world/gallery/2011/jan/19/tunisia-protests-pictures"
					sectionId "world"
					sectionName "World news"
					webPublicationDate "2011-01-19T15:01:09Z"
					webTitle "Tunisia protests continue in pictures "
					webUrl "http://www.guardian.co.uk/world/gallery/2011/jan/19/tunisia-protests-pictures"
					apiUrl "http://content.guardianapis.com/world/gallery/2011/jan/19/tunisia-protests-pictures"
				}
			}

			assert w.toString() ==
			'''{"response":{"status":"ok","userTier":"free","total":2413,"startIndex":1,"pageSize":10,"currentPage":1,"pages":242,"orderBy":"newest","results":[{"id":"world/video/2011/jan/19/tunisia-demonstrators-democracy-video","sectionId":"world","sectionName":"World news","webPublicationDate":"2011-01-19T15:12:46Z","webTitle":"Tunisian demonstrators demand new democracy - video","webUrl":"http://www.guardian.co.uk/world/video/2011/jan/19/tunisia-demonstrators-democracy-video","apiUrl":"http://content.guardianapis.com/world/video/2011/jan/19/tunisia-demonstrators-democracy-video"},{"id":"world/gallery/2011/jan/19/tunisia-protests-pictures","sectionId":"world","sectionName":"World news","webPublicationDate":"2011-01-19T15:01:09Z","webTitle":"Tunisia protests continue in pictures ","webUrl":"http://www.guardian.co.uk/world/gallery/2011/jan/19/tunisia-protests-pictures","apiUrl":"http://content.guardianapis.com/world/gallery/2011/jan/19/tunisia-protests-pictures"}]}}'''
		}
	}

	@Test
	@CompileStatic
	void testComplexStructureFromTheGuardianTypeSafe() {
		new StringWriter().with { Writer w ->
			def j = new JsonStreamBuilder( w )
			j 'response', {
				j 'status', "ok"
				j 'userTier', "free"
				j 'total', 2413
				j 'startIndex', 1
				j 'pageSize', 10
				j 'currentPage', 1
				j 'pages', 242
				j 'orderBy', "newest"
				j 'results', true, [
					id: "world/video/2011/jan/19/tunisia-demonstrators-democracy-video",
					sectionId: "world",
					sectionName: "World news",
					webPublicationDate: "2011-01-19T15:12:46Z",
					webTitle: "Tunisian demonstrators demand new democracy - video",
					webUrl: "http://www.guardian.co.uk/world/video/2011/jan/19/tunisia-demonstrators-democracy-video",
					apiUrl: "http://content.guardianapis.com/world/video/2011/jan/19/tunisia-demonstrators-democracy-video"
				]
				j 'results[]', {
					j 'id', "world/gallery/2011/jan/19/tunisia-protests-pictures"
					j 'sectionId', "world"
					j 'sectionName', "World news"
					j 'webPublicationDate', "2011-01-19T15:01:09Z"
					j 'webTitle', "Tunisia protests continue in pictures "
					j 'webUrl', "http://www.guardian.co.uk/world/gallery/2011/jan/19/tunisia-protests-pictures"
					j 'apiUrl', "http://content.guardianapis.com/world/gallery/2011/jan/19/tunisia-protests-pictures"
				}
			}

			assert w.toString() ==
			'''{"response":{"status":"ok","userTier":"free","total":2413,"startIndex":1,"pageSize":10,"currentPage":1,"pages":242,"orderBy":"newest","results":[{"id":"world/video/2011/jan/19/tunisia-demonstrators-democracy-video","sectionId":"world","sectionName":"World news","webPublicationDate":"2011-01-19T15:12:46Z","webTitle":"Tunisian demonstrators demand new democracy - video","webUrl":"http://www.guardian.co.uk/world/video/2011/jan/19/tunisia-demonstrators-democracy-video","apiUrl":"http://content.guardianapis.com/world/video/2011/jan/19/tunisia-demonstrators-democracy-video"},{"id":"world/gallery/2011/jan/19/tunisia-protests-pictures","sectionId":"world","sectionName":"World news","webPublicationDate":"2011-01-19T15:01:09Z","webTitle":"Tunisia protests continue in pictures ","webUrl":"http://www.guardian.co.uk/world/gallery/2011/jan/19/tunisia-protests-pictures","apiUrl":"http://content.guardianapis.com/world/gallery/2011/jan/19/tunisia-protests-pictures"}]}}'''
		}
	}

	@Test
	void testNestedListMap() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )
			json.content {
				'list[]' {
				}
				'list[]'(another:[a:[1, 2, 3]])
			}

			assert w.toString() == '''{"content":{"list":[{},{"another":{"a":[1,2,3]}}]}}'''
		}
	}

	@Test
	void testNestedListMap2() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )
			json.content {
				'list[]' {}
				'list[]' {
					another {
						a( [1, 2, 3])
					}
				}
			}

			assert w.toString() == '''{"content":{"list":[{},{"another":{"a":[1,2,3]}}]}}'''
		}
	}

	@Test
	void testTrendsFromTwitter() {
		new StringWriter().with { w ->
			def json = new JsonStreamBuilder( w )
			json.trends {
				"2010-06-22 17:20[]" (
						name: "Groovy rules",
						query: "Groovy rules"
						)
				"2010-06-22 17:20[]" {
					name "#worldcup"
					query "#worldcup"
				}
				"2010-06-22 17:20[]" (
						name: "Uruguai",
						query: "Uruguai"
						)
				"2010-06-22 06:20[]" {
					name "#groovy"
					query "#groovy"
				}
				"2010-06-22 06:20[]" (
						name: "#java",
						query: "#java"
						)
			}
			assert w.toString() == '''{"trends":{"2010-06-22 17:20":[{"name":"Groovy rules","query":"Groovy rules"},{"name":"#worldcup","query":"#worldcup"},{"name":"Uruguai","query":"Uruguai"}],"2010-06-22 06:20":[{"name":"#groovy","query":"#groovy"},{"name":"#java","query":"#java"}]}}'''
		}
	}

	@Test
	void testExampleFromTheGep7Page() {
		new StringWriter().with { w ->
			def builder = new JsonStreamBuilder( w )
			builder.people {
				person {
					firstName 'Guillame'
					lastName 'Laforge'
					// Maps are valid values for objects too
					address(
							city: 'Paris',
							country: 'France',
							zip: 12345,
							)
					married true
					conferences 'JavaOne', 'Gr8conf'
				}
			}

			assert w.toString() == '{"people":{"person":{"firstName":"Guillame","lastName":"Laforge","address":{"city":"Paris","country":"France","zip":12345},"married":true,"conferences":["JavaOne","Gr8conf"]}}}'
		}
	}

	@Test
	void testEdgeCases() {
		new StringWriter().with { w ->
			def builder = new JsonStreamBuilder( w )
			builder { elem 1, 2, 3 }

			assert w.toString() == '{"elem":[1,2,3]}'
		}
		new StringWriter().with { w ->
			def builder = new JsonStreamBuilder( w )
			builder.elem()

			assert w.toString() == '{"elem":{}}'
		}
		new StringWriter().with { w ->
			def builder = new JsonStreamBuilder( w )
			builder.elem(a: 1, b: 2) { c 3 }

			assert w.toString() == '{"elem":{"a":1,"b":2,"c":3}}'
		}
		new StringWriter().with { w ->
			def builder = new JsonStreamBuilder( w )
			builder.elem( [:] ) { c 3 }

			assert w.toString() == '{"elem":{"c":3}}'
		}
	}
}
