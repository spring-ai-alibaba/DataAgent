/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.SimpleVectorStoreFilterExpressionConverter;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/** Compiles metadata filters once per search, preserving numeric membership across types. */
final class SimpleVectorStoreMetadataFilter {

	private SimpleVectorStoreMetadataFilter() {
	}

	static Predicate<Map<String, Object>> compile(Filter.Operand operand) {
		if (operand == null) {
			return metadata -> true;
		}
		if (operand instanceof Filter.Group group) {
			return compile(group.content());
		}
		if (!(operand instanceof Filter.Expression expression)) {
			throw new IllegalArgumentException("Expected a filter expression or group");
		}
		return switch (expression.type()) {
			case AND -> compile(expression.left()).and(compile(expression.right()));
			case OR -> compile(expression.left()).or(compile(expression.right()));
			case NOT -> compile(expression.left()).negate();
			case IN, NIN -> membership(expression);
			default -> spelPredicate(expression);
		};
	}

	private static Predicate<Map<String, Object>> membership(Filter.Expression expression) {
		if (!(expression.left() instanceof Filter.Key key)
				|| !(expression.right() instanceof Filter.Value value) || !(value.value() instanceof List<?> values)) {
			throw new IllegalArgumentException("Membership requires a metadata key and a list of values");
		}
		Set<Object> candidates = new HashSet<>();
		values.forEach(candidate -> candidates.add(normalize(candidate)));
		Predicate<Map<String, Object>> predicate = metadata -> candidates.contains(normalize(metadata.get(key.key())));
		return expression.type() == Filter.ExpressionType.NIN ? predicate.negate() : predicate;
	}

	private static Object normalize(Object value) {
		if (value instanceof Number number) {
			// Keep integer precision (including IDs above 2^53) and fractional values.
			// Numeric strings remain strings; NaN/infinity retain ordinary equality.
			try {
				return new BigDecimal(number.toString()).stripTrailingZeros();
			}
			catch (NumberFormatException ex) {
				return value;
			}
		}
		return value;
	}

	private static Predicate<Map<String, Object>> spelPredicate(Filter.Expression filter) {
		String source = new SimpleVectorStoreFilterExpressionConverter().convertExpression(filter);
		var expression = new SpelExpressionParser().parseExpression(source);
		return metadata -> {
			StandardEvaluationContext context = new StandardEvaluationContext();
			context.setVariable("metadata", metadata);
			return Boolean.TRUE.equals(expression.getValue(context, Boolean.class));
		};
	}

}
