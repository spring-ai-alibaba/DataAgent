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

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.MetadataAwareSimpleVectorStore;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Provider-specific exact metadata retrieval without creating a query embedding. */
@Component
public class MetadataDocumentRetriever {

	private final Environment environment;

	public MetadataDocumentRetriever(Environment environment) {
		this.environment = environment;
	}

	public List<Document> find(VectorStore vectorStore, Filter.Expression filterExpression, int limit) {
		if (vectorStore instanceof MetadataAwareSimpleVectorStore simpleVectorStore) {
			return simpleVectorStore.findByFilter(filterExpression, limit);
		}
		if (vectorStore instanceof MilvusVectorStore milvusVectorStore) {
			return findInMilvus(milvusVectorStore, filterExpression, limit);
		}
		if (vectorStore instanceof ElasticsearchVectorStore elasticsearchVectorStore) {
			return findInElasticsearch(elasticsearchVectorStore, filterExpression, limit);
		}
		throw new IllegalArgumentException(
				"Exact metadata retrieval is not supported for " + vectorStore.getClass().getName());
	}

	private List<Document> findInMilvus(MilvusVectorStore vectorStore, Filter.Expression filterExpression, int limit) {
		VectorStoreObservationContext context = vectorStore.createObservationContextBuilder("metadata-query").build();
		String idField = environment.getProperty("spring.ai.vectorstore.milvus.id-field-name",
				MilvusVectorStore.DOC_ID_FIELD_NAME);
		String contentField = environment.getProperty("spring.ai.vectorstore.milvus.content-field-name",
				MilvusVectorStore.CONTENT_FIELD_NAME);
		String metadataField = environment.getProperty("spring.ai.vectorstore.milvus.metadata-field-name",
				MilvusVectorStore.METADATA_FIELD_NAME);
		MilvusServiceClient client = vectorStore.<MilvusServiceClient>getNativeClient()
			.orElseThrow(() -> new IllegalStateException("Milvus native client is unavailable"));
		QueryParam query = QueryParam.newBuilder()
			.withDatabaseName(context.getNamespace())
			.withCollectionName(context.getCollectionName())
			.withConsistencyLevel(ConsistencyLevelEnum.STRONG)
			.withOutFields(List.of(idField, contentField, metadataField))
			.withExpr(vectorStore.filterExpressionConverter.convertExpression(filterExpression))
			.withLimit((long) limit)
			.build();
		R<QueryResults> response = client.query(query);
		if (response.getException() != null) {
			throw new IllegalStateException("Milvus metadata query failed", response.getException());
		}
		Gson gson = new Gson();
		Type metadataType = new TypeToken<Map<String, Object>>() {
		}.getType();
		return new QueryResultsWrapper(response.getData()).getRowRecords().stream().map(row -> {
			JsonObject metadata = (JsonObject) row.get(metadataField);
			return Document.builder()
				.id(String.valueOf(row.get(idField)))
				.text((String) row.get(contentField))
				.metadata(metadata == null ? Map.of() : gson.fromJson(metadata, metadataType))
				.build();
		}).toList();
	}

	private List<Document> findInElasticsearch(ElasticsearchVectorStore vectorStore, Filter.Expression filterExpression,
			int limit) {
		var client = vectorStore.<co.elastic.clients.elasticsearch.ElasticsearchClient>getNativeClient()
			.orElseThrow(() -> new IllegalStateException("Elasticsearch native client is unavailable"));
		String indexName = vectorStore.createObservationContextBuilder("metadata-query").build().getCollectionName();
		Query query = toElasticsearchQuery(filterExpression);
		try {
			return client.search(search -> search.index(indexName).query(query).size(limit), Document.class)
				.hits()
				.hits()
				.stream()
				.map(hit -> hit.source())
				.filter(Objects::nonNull)
				.toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Elasticsearch metadata query failed", ex);
		}
	}

	/**
	 * Convert a metadata filter expression into an exact-match Elasticsearch query.
	 * Unlike the Lucene {@code query_string} generated by
	 * {@code ElasticsearchAiSearchFilterExpressionConverter}, this builds a native
	 * {@code bool}/{@code term}/{@code terms} query:
	 * <ul>
	 * <li>String values are matched on the {@code metadata.<key>.keyword} sub-field
	 * (dynamic string mapping) so values are never analyzed or treated as free text;</li>
	 * <li>multi-value {@code IN} clauses become a single {@code terms} clause instead of
	 * one OR branch per value, which keeps the query below Elasticsearch's
	 * {@code indices.query.bool.max_clause_count} (default 1024) even for hundreds of
	 * table names.</li>
	 * </ul>
	 */
	static Query toElasticsearchQuery(Filter.Expression expression) {
		return toQuery(expression);
	}

	private static Query toQuery(Filter.Operand operand) {
		if (operand instanceof Filter.Group group) {
			return toQuery(group.content());
		}
		if (operand instanceof Filter.Expression expression) {
			return toQuery(expression);
		}
		throw new IllegalArgumentException("Unsupported filter operand type: " + operand.getClass().getSimpleName());
	}

	private static Query toQuery(Filter.Expression expression) {
		return switch (expression.type()) {
			case AND -> {
				Query left = toQuery(expression.left());
				Query right = toQuery(expression.right());
				yield Query.of(q -> q.bool(b -> b.must(left, right)));
			}
			case OR -> {
				Query left = toQuery(expression.left());
				Query right = toQuery(expression.right());
				yield Query.of(q -> q.bool(b -> b.should(left, right).minimumShouldMatch("1")));
			}
			case NOT -> {
				Query inner = toQuery(expression.left());
				yield Query.of(q -> q.bool(b -> b.mustNot(inner)));
			}
			case EQ, NE -> {
				Query match = matchQuery(keyOf(expression), valueOf(expression));
				if (expression.type() == ExpressionType.NE) {
					yield Query.of(q -> q.bool(b -> b.mustNot(match)));
				}
				yield match;
			}
			case IN, NIN -> {
				Query match = matchQuery(keyOf(expression), valuesOf(expression));
				if (expression.type() == ExpressionType.NIN) {
					yield Query.of(q -> q.bool(b -> b.mustNot(match)));
				}
				yield match;
			}
			case ISNULL -> Query.of(q -> q.bool(b -> b.mustNot(existsQuery(keyOf(expression)))));
			case ISNOTNULL -> existsQuery(keyOf(expression));
			default -> throw new UnsupportedOperationException(
					"Unsupported filter expression type for exact metadata query: " + expression.type());
		};
	}

	private static Key keyOf(Filter.Expression expression) {
		return (Key) expression.left();
	}

	private static Object valueOf(Filter.Expression expression) {
		return ((Filter.Value) expression.right()).value();
	}

	@SuppressWarnings("unchecked")
	private static List<Object> valuesOf(Filter.Expression expression) {
		return (List<Object>) ((Filter.Value) expression.right()).value();
	}

	private static Query matchQuery(Key key, Object value) {
		if (value instanceof List<?> list) {
			return termsQuery(key, list);
		}
		return termQuery(key, value);
	}

	private static Query termQuery(Key key, Object value) {
		if (value instanceof String string) {
			// Dynamic string mapping creates metadata.<key>.keyword for exact match.
			return Query.of(q -> q.term(t -> t.field(keywordField(key)).value(string)));
		}
		return Query.of(q -> q.term(t -> t.field(metadataField(key)).value(toFieldValue(value))));
	}

	private static Query termsQuery(Key key, List<?> values) {
		if (values.isEmpty()) {
			return Query.of(q -> q.matchNone(mn -> mn));
		}
		boolean allStrings = values.stream().allMatch(value -> value instanceof String);
		String field = allStrings ? keywordField(key) : metadataField(key);
		List<FieldValue> fieldValues = values.stream().map(MetadataDocumentRetriever::toFieldValue).toList();
		return Query.of(q -> q.terms(t -> t.field(field).terms(ts -> ts.value(fieldValues))));
	}

	private static Query existsQuery(Key key) {
		return Query.of(q -> q.exists(e -> e.field(metadataField(key))));
	}

	private static FieldValue toFieldValue(Object value) {
		if (value instanceof String string) {
			return FieldValue.of(string);
		}
		if (value instanceof Boolean bool) {
			return FieldValue.of(bool);
		}
		if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
			return FieldValue.of(((Number) value).longValue());
		}
		if (value instanceof Double || value instanceof Float) {
			return FieldValue.of(((Number) value).doubleValue());
		}
		return FieldValue.of(String.valueOf(value));
	}

	private static String metadataField(Key key) {
		return "metadata." + key.key();
	}

	private static String keywordField(Key key) {
		return metadataField(key) + ".keyword";
	}

}
