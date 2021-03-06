/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher

import org.scalatest.Assertions
import org.junit.Test
import org.neo4j.graphdb.Relationship

class MergeRelationshipAcceptanceTest
  extends ExecutionEngineHelper with Assertions with StatisticsChecker {

  @Test
  def should_be_able_to_create_relationship() {
    // given
    val a = createNode("A")
    val b = createNode("B")

    // when
    val r = executeScalar("MATCH (a {name:'A'}), (b {name:'B'}) MERGE (a)-[r:TYPE]->(b) RETURN r").asInstanceOf[Relationship]

    // then
    graph.inTx {
      assert(r.getStartNode === a)
      assert(r.getEndNode === b)
      assert(r.getType.name() === "TYPE")
    }
  }

  @Test
  def should_be_able_to_find_a_relationship() {
    // given
    val a = createNode("A")
    val b = createNode("B")
    val r1 = relate(a, b, "TYPE")

    // when
    val result = executeScalar("MATCH (a {name:'A'}), (b {name:'B'}) MERGE (a)-[r:TYPE]->(b) RETURN r").asInstanceOf[Relationship]

    // then
    assert(r1 === result)
  }

  @Test
  def should_be_able_to_find_two_existing_relationships() {
    // given
    val a = createNode("A")
    val b = createNode("B")
    val r1 = relate(a, b, "TYPE")
    val r2 = relate(a, b, "TYPE")

    // when
    val result = execute("MATCH (a {name:'A'}), (b {name:'B'}) MERGE (a)-[r:TYPE]->(b) RETURN r").columnAs[Relationship]("r").toList

    // then
    assert(List(r1, r2) === result)
  }

  @Test
  def should_be_able_to_find_two_relationships() {
    // given
    val a = createNode("A")
    val b = createNode("B")
    val r1 = relate(a, b, "TYPE")
    val r2 = relate(a, b, "TYPE")

    // when
    val result = execute("MATCH (a {name:'A'}), (b {name:'B'}) MERGE (a)-[r:TYPE]->(b) RETURN r").columnAs[Relationship]("r")

    // then
    assert(Set(r1, r2) === result.toSet)
  }

  @Test
  def should_be_able_to_filter_out_relationships() {
    // given
    val a = createNode("A")
    val b = createNode("B")
    relate(a, b, "TYPE", "r1")
    val r = relate(a, b, "TYPE", "r2")

    // when
    val result = executeScalar("MATCH (a {name:'A'}), (b {name:'B'}) MERGE (a)-[r:TYPE {name:'r2'}]->(b) RETURN r").asInstanceOf[Relationship]

    // then
    assert(r === result)
  }

  @Test
  def should_be_able_to_create_when_nothing_matches() {
    // given
    val a = createNode("A")
    val b = createNode("B")
    relate(a, b, "TYPE", "r1")

    // when
    val r = executeScalar("MATCH (a {name:'A'}), (b {name:'B'}) MERGE (a)-[r:TYPE {name:'r2'}]->(b) RETURN r").asInstanceOf[Relationship]

    // then
    graph.inTx {
      assert(r.getStartNode === a)
      assert(r.getEndNode === b)
      assert(r.getType.name() === "TYPE")
    }
  }

  @Test
  def should_not_be_fooled_by_direction() {
    // given
    val a = createNode("A")
    val b = createNode("B")
    val r = relate(b, a, "TYPE")
    val r2 = relate(a, b, "TYPE")

    // when
    val result = execute("MATCH (a {name:'A'}), (b {name:'B'}) MERGE (a)<-[r:TYPE]-(b) RETURN r")

    // then
    assertStats(result, relationshipsCreated = 0)
    assert(result.toList === List(Map("r" -> r)))
  }

  @Test
  def should_create_relationship_with_property() {
    // given
    val a = createNode("A")
    val b = createNode("B")

    // when
    val result = execute("MATCH (a {name:'A'}), (b {name:'B'}) MERGE (a)-[r:TYPE {name:'Lola'}]->(b) RETURN r")

    // then
    assertStats(result, relationshipsCreated = 1, propertiesSet = 1)
    graph.inTx {
      val r = result.toList.head("r").asInstanceOf[Relationship]
      assert(r.getProperty("name") === "Lola")
      assert(r.getType.name() === "TYPE")
      assert(r.getStartNode === a)
      assert(r.getEndNode === b)
    }
  }

  @Test
  def should_handle_on_create() {
    // given
    val a = createNode("A")
    val b = createNode("B")

    // when
    val result = execute("MATCH (a {name:'A'}), (b {name:'B'}) MERGE (a)-[r:TYPE]->(b) ON CREATE SET r.name = 'Lola' RETURN r")

    // then
    assertStats(result, relationshipsCreated = 1, propertiesSet = 1)
    graph.inTx {
      val r = result.toList.head("r").asInstanceOf[Relationship]
      assert(r.getProperty("name") === "Lola")
      assert(r.getType.name() === "TYPE")
      assert(r.getStartNode === a)
      assert(r.getEndNode === b)
    }
  }

  @Test
  def should_handle_on_match() {
    // given
    val a = createNode("A")
    val b = createNode("B")
    relate(a, b, "TYPE")

    // when
    val result = execute("MATCH (a {name:'A'}), (b {name:'B'}) MERGE (a)-[r:TYPE]->(b) ON MATCH SET r.name = 'Lola' RETURN r")

    // then
    assertStats(result, relationshipsCreated = 0, propertiesSet = 1)
    graph.inTx {
      val r = result.toList.head("r").asInstanceOf[Relationship]
      assert(r.getProperty("name") === "Lola")
      assert(r.getType.name() === "TYPE")
      assert(r.getStartNode === a)
      assert(r.getEndNode === b)
    }
  }

  @Test
  def should_work_with_single_bound_node() {
    // given
    val a = createNode("A")

    // when
    val result = execute("MATCH (a {name:'A'}) MERGE (a)-[r:TYPE]->() RETURN r")

    // then
    assertStats(result, relationshipsCreated = 1, nodesCreated = 1)
    graph.inTx {
      val r = result.toList.head("r").asInstanceOf[Relationship]
      assert(r.getType.name() === "TYPE")
      assert(r.getStartNode === a)
    }
  }

  @Test
  def should_handle_longer_patterns() {
    // given
    val a = createNode("A")

    // when
    val result = execute("MATCH (a {name:'A'}) MERGE (a)-[r:TYPE]->()<-[:TYPE]-(b) RETURN r")

    // then
    assertStats(result, relationshipsCreated = 2, nodesCreated = 2)
    graph.inTx {
      val r = result.toList.head("r").asInstanceOf[Relationship]
      assert(r.getType.name() === "TYPE")
      assert(r.getStartNode === a)
    }
  }

  @Test
  def should_handle_nodes_bound_in_the_middle() {
    // given
    val b = createNode("B")

    // when
    val result = execute("MATCH (b {name:'B'}) MERGE (a)-[r1:TYPE]->(b)<-[r2:TYPE]-(c) RETURN r1, r2")

    // then
    assertStats(result, relationshipsCreated = 2, nodesCreated = 2)
    val resultMap = result.toList.head
    graph.inTx {
      val r1 = resultMap("r1").asInstanceOf[Relationship]
      assert(r1.getType.name() === "TYPE")
      assert(r1.getEndNode === b)

      val r2 = resultMap("r2").asInstanceOf[Relationship]
      assert(r2.getType.name() === "TYPE")
      assert(r2.getEndNode === b)
    }
  }

  @Test
  def should_handle_nodes_bound_in_the_middle_when_half_pattern_is_matching() {
    // given
    val a = createLabeledNode("A")
    val b = createLabeledNode("B")
    relate(a, b, "TYPE")

    // when
    val result = execute("MATCH (b:B) MERGE (a:A)-[r1:TYPE]->(b)<-[r2:TYPE]-(c:C) RETURN r1, r2")

    // then
    assertStats(result, relationshipsCreated = 2, nodesCreated = 2, labelsAdded = 2)
    val resultMap = result.toList.head
    graph.inTx {
      val r1 = resultMap("r1").asInstanceOf[Relationship]
      assert(r1.getType.name() === "TYPE")
      assert(r1.getEndNode === b)

      val r2 = resultMap("r2").asInstanceOf[Relationship]
      assert(r2.getType.name() === "TYPE")
      assert(r2.getEndNode === b)
    }
  }

  @Test
  def should_handle_first_declaring_nodes_and_then_creating_relationships_between_them() {
    // given
    val a = createLabeledNode("A")
    val b = createLabeledNode("B")

    // when
    val result = execute("MERGE (a:A) MERGE (b:B) MERGE (a)-[:FOO]->(b)")

    // then
    assertStats(result, relationshipsCreated = 1)
  }

  @Test
  def should_handle_building_links_mixing_create_with_merge_pattern() {
    // given

    // when
    val result = execute("CREATE (a:A) MERGE (a)-[:KNOWS]->(b:B) CREATE (b)-[:KNOWS]->(c:C) RETURN a, b, c")

    // then
    assertStats(result, relationshipsCreated = 2, nodesCreated = 3, labelsAdded = 3)
  }

  @Test
  def when_merging_a_pattern_that_includes_a_unique_node_constraint_violation_fail() {
    // given
    graph.createConstraint("Person", "id")
    createLabeledNode(Map("id"->666), "Person")

    // when then fails
    intercept[CypherExecutionException](execute("CREATE (a:A) MERGE (a)-[:KNOWS]->(:Person {id:666})"))
  }

  @Test def should_work_well_inside_foreach() {
    val a = createLabeledNode("Start")
    relate(a, createNode("prop" -> 2), "FOO")

    val result = execute("match (a:Start) foreach(x in [1,2,3] | merge (a)-[:FOO]->({prop: x}) )")
    assertStats(result, nodesCreated = 2, propertiesSet = 2, relationshipsCreated = 2)
  }

  @Test def should_handle_two_merges_inside_foreach() {
    val a = createLabeledNode("Start")
    val b = createLabeledNode(Map("prop" -> 42), "End")

    val result = execute("match (a:Start) foreach(x in [42] | merge (b:End {prop: x}) merge (a)-[:FOO]->(b) )")
    assertStats(result, nodesCreated = 0, propertiesSet = 0, relationshipsCreated = 1)

    graph.inTx {
      val rel = a.getRelationships.iterator().next()
      assert(rel.getStartNode === a)
      assert(rel.getEndNode === b)
    }
  }
}
