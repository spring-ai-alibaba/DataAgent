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
package com.alibaba.cloud.ai.dataagent.agentscope.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class QueryClarifyService {

	private static final Pattern EXPLICIT_TIME_PATTERN = Pattern.compile(
			"(今天|今日|昨天|昨日|本周|上周|本月|上月|本季度|上季度|今年|去年|最近\\s*\\d+\\s*(天|周|个月|月|年)|\\d{4}年(?:\\d{1,2}月(?:\\d{1,2}日)?)?|\\d{4}[-/]\\d{1,2}(?:[-/]\\d{1,2})?|Q[1-4]|第[一二三四1-4]季度|同期)");

	private static final Pattern TIME_SENSITIVE_PATTERN = Pattern.compile(
			"(销量|销售额|成交额|收入|营收|GMV|gmv|订单|下单|支付|活跃|新增|留存|流失|复购|趋势|波动|变化|环比|同比|增长|下降)");

	private static final Pattern METRIC_AMBIGUOUS_PATTERN = Pattern.compile(
			"(GMV|gmv|销售额|成交额|收入|营收|销量|订单量|转化率|留存|复购|活跃|新增|流失|客单价|毛利|利润|高价值用户)");

	private static final Pattern METRIC_DEFINED_PATTERN = Pattern.compile(
			"(按.*口径|口径.*为|定义为|这里的.*指|仅统计|只统计|以.*为准|GMV=.*|销售额=.*|转化率=.*|留存=.*)");

	private static final Pattern COMPARISON_PATTERN = Pattern.compile("(对比|比较|同比|环比|较|增长|下降|变化|波动)");

	private static final Pattern COMPARISON_DEFINED_PATTERN = Pattern.compile(
			"(与[^，。；\\s]+(对比|比较)?|和[^，。；\\s]+(对比|比较)?|相比|较上周|较上月|较去年|较去年同期|较上期|同比|环比)");

	private static final Pattern ORDERING_PATTERN = Pattern.compile(
			"(排序|排名|TOP\\s*\\d+|Top\\s*\\d+|top\\s*\\d+|前\\s*\\d+|后\\s*\\d+|最高|最低|最多|最少)");

	private static final Pattern ORDERING_DEFINED_PATTERN = Pattern.compile(
			"(按[^，。；\\s]+(排序|排名)|基于[^，。；\\s]+(排序|排名)|按照[^，。；\\s]+(排序|排名)|按[^，。；\\s]+(升序|降序)|升序|降序|从高到低|从低到高)");

	private static final Pattern ORDERING_METRIC_PATTERN = Pattern.compile(
			"(库存|价格|单价|销量|销售额|成交额|收入|营收|GMV|gmv|订单量|数量|下单量|支付金额)");

	private static final Pattern STATIC_ANALYSIS_PATTERN = Pattern.compile("(库存|价格|单价|成本|邮箱|用户名|分类|状态值|字段|列|表结构)");

	public QueryClarifyAssessment assess(@Nullable String query, @Nullable String humanFeedbackContent) {
		String normalizedQuery = normalize(query);
		String normalizedFeedback = normalize(humanFeedbackContent);
		if (!StringUtils.hasText(normalizedQuery)) {
			return QueryClarifyAssessment.low(normalizedQuery, normalizedFeedback);
		}
		String evidenceText = normalizedQuery + (StringUtils.hasText(normalizedFeedback) ? "\n" + normalizedFeedback : "");

		List<String> missingDimensions = new ArrayList<>();
		List<String> followUpQuestions = new ArrayList<>();
		List<String> suggestedAssumptions = new ArrayList<>();
		int score = 0;

		if (requiresTimeContext(normalizedQuery) && !hasExplicitTime(evidenceText)) {
			missingDimensions.add("时间范围");
			followUpQuestions.add("你希望统计哪个时间范围？例如最近30天、本月、2025年6月。");
			suggestedAssumptions.add("按最近30天统计");
			score += 1;
		}
		if (requiresMetricDefinition(normalizedQuery) && !hasMetricDefinition(evidenceText)) {
			missingDimensions.add("指标口径");
			followUpQuestions.add("你说的指标按什么口径计算？例如 GMV 是否只统计已完成订单、是否含退款。");
			suggestedAssumptions.add("按已完成订单口径统计，且不含退款");
			score += 2;
		}
		if (requiresComparisonTarget(normalizedQuery) && !hasComparisonTarget(evidenceText)) {
			missingDimensions.add("对比对象");
			followUpQuestions.add("你希望和谁比较？例如与上周、上月、去年同期或某个具体对象对比。");
			suggestedAssumptions.add("与上周同期对比");
			score += 2;
		}
		if (requiresOrdering(normalizedQuery) && !hasOrderingBasis(evidenceText)) {
			missingDimensions.add("排序依据");
			followUpQuestions.add("你希望按什么指标排序，以及升序还是降序？例如按销售额降序。");
			suggestedAssumptions.add("按销售额降序");
			score += 2;
		}

		QueryClarifyRiskLevel riskLevel = score >= 3 ? QueryClarifyRiskLevel.HIGH
				: score >= 1 ? QueryClarifyRiskLevel.MEDIUM : QueryClarifyRiskLevel.LOW;
		boolean clarifyRequired = riskLevel == QueryClarifyRiskLevel.HIGH;
		String summary = buildSummary(riskLevel, missingDimensions, normalizedFeedback);
		String userMessage = clarifyRequired
				? buildClarifyMessage(riskLevel, followUpQuestions, suggestedAssumptions, normalizedFeedback)
				: buildPassThroughMessage(riskLevel, missingDimensions, normalizedFeedback);
		return new QueryClarifyAssessment(normalizedQuery, normalizedFeedback, riskLevel, clarifyRequired,
				List.copyOf(missingDimensions), List.copyOf(followUpQuestions), List.copyOf(suggestedAssumptions), summary,
				userMessage);
	}

	private boolean requiresTimeContext(String query) {
		if (!StringUtils.hasText(query)) {
			return false;
		}
		if (STATIC_ANALYSIS_PATTERN.matcher(query).find() && !TIME_SENSITIVE_PATTERN.matcher(query).find()) {
			return false;
		}
		return TIME_SENSITIVE_PATTERN.matcher(query).find() || COMPARISON_PATTERN.matcher(query).find()
				|| ORDERING_PATTERN.matcher(query).find();
	}

	private boolean requiresMetricDefinition(String query) {
		return StringUtils.hasText(query) && METRIC_AMBIGUOUS_PATTERN.matcher(query).find();
	}

	private boolean requiresComparisonTarget(String query) {
		return StringUtils.hasText(query) && COMPARISON_PATTERN.matcher(query).find();
	}

	private boolean requiresOrdering(String query) {
		return StringUtils.hasText(query) && ORDERING_PATTERN.matcher(query).find();
	}

	private boolean hasExplicitTime(String text) {
		return StringUtils.hasText(text) && EXPLICIT_TIME_PATTERN.matcher(text).find();
	}

	private boolean hasMetricDefinition(String text) {
		return StringUtils.hasText(text) && METRIC_DEFINED_PATTERN.matcher(text).find();
	}

	private boolean hasComparisonTarget(String text) {
		return StringUtils.hasText(text) && COMPARISON_DEFINED_PATTERN.matcher(text).find();
	}

	private boolean hasOrderingBasis(String text) {
		if (!StringUtils.hasText(text)) {
			return false;
		}
		if (ORDERING_DEFINED_PATTERN.matcher(text).find()) {
			return true;
		}
		return ORDERING_METRIC_PATTERN.matcher(text).find() && ORDERING_PATTERN.matcher(text).find();
	}

	private String buildSummary(QueryClarifyRiskLevel riskLevel, List<String> missingDimensions,
			String feedbackContent) {
		if (riskLevel == QueryClarifyRiskLevel.LOW) {
			return StringUtils.hasText(feedbackContent) ? "已收到补充信息，当前歧义等级较低，可继续执行。"
					: "当前问题歧义等级较低，可直接继续执行。";
		}
		String prefix = StringUtils.hasText(feedbackContent) ? "已收到补充信息，但仍存在歧义：" : "检测到高歧义问题：";
		return prefix + String.join("、", missingDimensions);
	}

	private String buildClarifyMessage(QueryClarifyRiskLevel riskLevel, List<String> followUpQuestions,
			List<String> suggestedAssumptions, String feedbackContent) {
		StringBuilder builder = new StringBuilder();
		builder.append("为避免在口径不清时直接查库，当前问题需要先澄清。")
			.append(System.lineSeparator())
			.append("riskLevel=").append(riskLevel.value()).append(System.lineSeparator());
		if (StringUtils.hasText(feedbackContent)) {
			builder.append("已收到你的补充，但还不足以消除关键歧义。").append(System.lineSeparator());
		}
		builder.append("请先补充以下信息：").append(System.lineSeparator());
		for (int i = 0; i < followUpQuestions.size(); i++) {
			builder.append(i + 1).append(". ").append(followUpQuestions.get(i)).append(System.lineSeparator());
		}
		if (!suggestedAssumptions.isEmpty()) {
			builder.append("如果你接受默认假设，也可以直接回复：").append(System.lineSeparator());
			builder.append("按以下假设继续：").append(String.join("；", suggestedAssumptions));
		}
		return builder.toString().trim();
	}

	private String buildPassThroughMessage(QueryClarifyRiskLevel riskLevel, List<String> missingDimensions,
			String feedbackContent) {
		if (riskLevel == QueryClarifyRiskLevel.LOW) {
			return StringUtils.hasText(feedbackContent) ? "已收到补充说明，继续按更新后的上下文执行。"
					: "当前问题歧义较低，可继续执行。";
		}
		return "当前问题存在轻度歧义（" + String.join("、", missingDimensions) + "），继续执行前会优先保留你的显式口径。";
	}

	private String normalize(@Nullable String text) {
		if (!StringUtils.hasText(text)) {
			return "";
		}
		return text.trim().replace('\u3000', ' ');
	}

	public record QueryClarifyAssessment(String query, String feedbackContent, QueryClarifyRiskLevel riskLevel,
			boolean clarifyRequired, List<String> missingDimensions, List<String> followUpQuestions,
			List<String> suggestedAssumptions, String summary, String userMessage) {

		private static QueryClarifyAssessment low(String query, String feedbackContent) {
			return new QueryClarifyAssessment(query, feedbackContent, QueryClarifyRiskLevel.LOW, false, List.of(),
					List.of(), List.of(), "当前问题歧义等级较低，可直接继续执行。", "当前问题歧义较低，可继续执行。");
		}

		public boolean shouldBlockExecution() {
			return clarifyRequired && riskLevel == QueryClarifyRiskLevel.HIGH;
		}

		public Map<String, Object> toMetadata() {
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("clarifyRequired", clarifyRequired);
			metadata.put("riskLevel", riskLevel.value());
			metadata.put("missingDimensions", missingDimensions);
			metadata.put("followUpQuestions", followUpQuestions);
			metadata.put("suggestedAssumptions", suggestedAssumptions);
			metadata.put("summary", summary);
			return metadata;
		}
	}

	public enum QueryClarifyRiskLevel {

		LOW,

		MEDIUM,

		HIGH;

		public String value() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

}
