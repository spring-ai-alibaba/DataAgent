/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that metadata filters with hundreds of table names are translated into a
 * compact, exact-match Elasticsearch query instead of an exploding Lucene
 * {@code query_string} (the root cause of {@code search_phase_execution_exception: all
 * shards failed} in issue #608).
 */
class MetadataDocumentRetrieverQueryTest {

	private static final int DATASOURCE_ID = 9;

	private static List<String> tableNames(int count) {
		return IntStream.rangeClosed(1, count).mapToObj(i -> "table_" + i).toList();
	}

	private static Filter.Expression searchTablesFilter(Integer datasourceId, List<String> tableNames) {
		return DynamicFilterService.buildFilterExpressionForSearchTables(datasourceId, tableNames);
	}

	/**
	 * combineWithAnd builds a left-deep AND tree which maps to nested bool queries.
	 * Recursively flatten plain must-bools so one AND conjunct = one leaf clause (unlike
	 * the query_string form that expands each IN value into its own OR branch).
	 */
	private static List<Query> mustLeaves(Query query) {
		List<Query> leaves = new ArrayList<>();
		collectMustLeaves(query, leaves);
		return leaves;
	}

	private static void collectMustLeaves(Query query, List<Query> leaves) {
		if (!query.isBool()) {
			leaves.add(query);
			return;
		}
		var bool = query.bool();
		if (bool.must() != null && !bool.must().isEmpty() && bool.should().isEmpty() && bool.mustNot().isEmpty()) {
			bool.must().forEach(child -> collectMustLeaves(child, leaves));
		}
		else {
			leaves.add(query);
		}
	}

	@Test
	void threeHundredTableNamesCollapseIntoSingleTermsClauseOnKeywordField() {
		List<String> tableNames = tableNames(320);

		Query query = MetadataDocumentRetriever.toElasticsearchQuery(searchTablesFilter(DATASOURCE_ID, tableNames));

		// each AND conjunct maps to exactly one leaf clause: term datasourceId, term
		// vectorType, terms name
		List<Query> leaves = mustLeaves(query);
		assertEquals(3, leaves.size(), "Each AND conjunct must map to exactly one leaf clause");

		Query datasourceIdClause = leaves.get(0);
		assertTrue(datasourceIdClause.isTerm());
		assertEquals("metadata.datasourceId.keyword", datasourceIdClause.term().field());
		assertEquals(String.valueOf(DATASOURCE_ID), datasourceIdClause.term().value().stringValue());

		Query vectorTypeClause = leaves.get(1);
		assertTrue(vectorTypeClause.isTerm());
		assertEquals("metadata.vectorType.keyword", vectorTypeClause.term().field());
		assertEquals(DocumentMetadataConstant.TABLE, vectorTypeClause.term().value().stringValue());

		// 300+ names collapse into a single terms clause: below max_clause_count, and no
		// query_string is emitted
		Query nameClause = leaves.get(2);
		assertTrue(nameClause.isTerms());
		assertEquals("metadata.name.keyword", nameClause.terms().field());
		List<String> values = nameClause.terms().terms().value().stream().map(v -> v.stringValue()).toList();
		assertEquals(320, values.size());
		assertEquals(new HashSet<>(tableNames), new HashSet<>(values));

		// all clauses are structured term/terms, never a query_string (which would
		// analyze values and re-scatter bare words across the default field)
		for (Query clause : leaves) {
			assertFalse(clause.isQueryString(), "Exact metadata retrieval must not use query_string");
		}
	}

	@Test
	void eqOnlyFilterBecomesSingleTermQuery() {
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		Filter.Expression filter = b.eq(Constant.DATASOURCE_ID, String.valueOf(DATASOURCE_ID)).build();

		Query query = MetadataDocumentRetriever.toElasticsearchQuery(filter);

		assertTrue(query.isTerm());
		assertEquals("metadata.datasourceId.keyword", query.term().field());
		assertEquals(String.valueOf(DATASOURCE_ID), query.term().value().stringValue());
	}

	@Test
	void columnDocumentsQueryUsesTableNameKeywordTerms() {
		List<String> upstreamTableNames = tableNames(300);
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Filter.Expression> conditions = List.of(
				b.eq(Constant.DATASOURCE_ID, String.valueOf(DATASOURCE_ID)).build(),
				b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.COLUMN).build(),
				b.in(DocumentMetadataConstant.TABLE_NAME, upstreamTableNames.toArray()).build());
		Filter.Expression filter = DynamicFilterService.combineWithAnd(conditions);

		Query query = MetadataDocumentRetriever.toElasticsearchQuery(filter);

		List<Query> leaves = mustLeaves(query);
		assertEquals(3, leaves.size());
		Query tableNameClause = leaves.get(2);
		assertTrue(tableNameClause.isTerms());
		assertEquals("metadata.tableName.keyword", tableNameClause.terms().field());
		assertEquals(300, tableNameClause.terms().terms().value().size());
	}

	@Test
	void notInFilterWrapsTermsInMustNot() {
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<String> excluded = List.of("orders", "products");
		Filter.Expression filter = b.nin(DocumentMetadataConstant.NAME, excluded.toArray()).build();

		Query query = MetadataDocumentRetriever.toElasticsearchQuery(filter);

		assertTrue(query.isBool());
		assertEquals(1, query.bool().mustNot().size());
		Query inner = query.bool().mustNot().get(0);
		assertTrue(inner.isTerms());
		assertEquals("metadata.name.keyword", inner.terms().field());
		Set<String> values = new HashSet<>(inner.terms().terms().value().stream().map(v -> v.stringValue()).toList());
		assertEquals(new HashSet<>(excluded), values);
	}

	@Test
	void allInValuesStayBoundToKeywordField() {
		// regression guard: the old converter only prefixed the first IN value with the
		// field; every value must stay bound to the .keyword field here
		List<String> tableNames = tableNames(50);
		Filter.Expression filter = searchTablesFilter(DATASOURCE_ID, tableNames);

		Query query = MetadataDocumentRetriever.toElasticsearchQuery(filter);

		var terms = mustLeaves(query).get(2).terms().terms().value();
		List<String> actual = new ArrayList<>();
		for (int i = 0; i < terms.size(); i++) {
			actual.add(terms.get(i).stringValue());
		}
		assertEquals(tableNames, actual);
	}

}
