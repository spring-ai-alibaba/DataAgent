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
package com.alibaba.cloud.ai.dataagent.service.langfuse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 每个图节点的输入/输出 state key 白名单，供 Langfuse 节点级 span 精确记录 input/output 使用。
 *
 * <p>
 * 为什么需要白名单：{@code OverAllState} 是单调累加的（早期节点的输出会一直留在 state 里）。若直接把整份 state
 * 当作某个节点的输入，靠后的节点 span 会把前面所有节点的输出都当成自己的输入，失去排查价值。
 *
 * <p>
 * <b>登记规则（最易踩的坑）</b>：DataAgent 的节点普遍返回 embedded {@code Flux}，output key 存在两层结构：
 *
 * <pre>
 * return Map.of(SCHEMA_RECALL_NODE_OUTPUT, generator);   // 外层：Flux 信封标签，永不进 state
 *         ↓ Flux 消费完后 resultSupplier 才产出真正的 state 更新
 * return Map.of(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, ..., // 内层：这才是落进 state 的
 *               COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, ...);
 * </pre>
 *
 * 框架在合并 state 前会过滤掉 {@code instanceof Flux} 的条目，因此本表只登记<b>内层真实 state
 * key</b>。已知的纯信封标签见 {@link #FLUX_ENVELOPE_KEYS}。
 *
 * <p>
 * 同一节点若有多条执行路径（正常/降级/重试超限等），本表登记<b>所有路径</b>的 output key 的并集。
 *
 * @author suke
 */
public final class NodeIoRegistry {

	/**
	 * {@code HumanFeedbackNode} 写入的路由 key。源码中是裸字符串字面量（非 {@code Constant} 常量），
	 * 见 {@code HumanFeedbackNode} 与 {@code HumanFeedbackDispatcher}，此处照抄以保持一致。
	 */
	public static final String HUMAN_NEXT_NODE = "human_next_node";

	/**
	 * 纯 Flux 信封标签：只作为 {@code apply()} 返回值的外层 key 出现，<b>永远不会进入 state</b>。
	 *
	 * <p>
	 * 这两个 key 绝不能出现在任何节点的 output 白名单里。注意
	 * {@code PYTHON_ANALYSIS_NODE_OUTPUT} 虽然在 {@code keyStrategyHashMap} 中注册了
	 * {@code KeyStrategy}，但它同样只是信封标签——"已注册 KeyStrategy" 并不能证明一个 key 会真正落进 state。
	 */
	public static final Set<String> FLUX_ENVELOPE_KEYS = Set.of(SCHEMA_RECALL_NODE_OUTPUT,
			PYTHON_ANALYSIS_NODE_OUTPUT);

	private static final Map<String, NodeIo> NODE_IO = buildRegistry();

	private NodeIoRegistry() {
	}

	/**
	 * 某个节点声明的输入/输出 state key。
	 *
	 * @param inputKeys 该节点从 state 读取的 key（含经 {@code StateUtil}/{@code PlanProcessUtil}
	 * 辅助方法间接读取的）
	 * @param outputKeys 该节点所有执行路径写入 state 的 key 的并集（内层真实 key）
	 */
	public record NodeIo(List<String> inputKeys, List<String> outputKeys) {
	}

	private static Map<String, NodeIo> buildRegistry() {
		Map<String, NodeIo> registry = new LinkedHashMap<>();

		// 输入：用户原始提问 + 多轮上下文；输出：闲聊分支额外写 FINAL_ANSWER
		registry.put(INTENT_RECOGNITION_NODE,
				new NodeIo(List.of(INPUT_KEY, MULTI_TURN_CONTEXT),
						List.of(INTENT_RECOGNITION_NODE_OUTPUT, FINAL_ANSWER)));

		// 各种"无 evidence"分支同样写 EVIDENCE（值为 "无" 或 ""），故 output 恒为单 key
		registry.put(EVIDENCE_RECALL_NODE,
				new NodeIo(List.of(INPUT_KEY, AGENT_ID, MULTI_TURN_CONTEXT), List.of(EVIDENCE)));

		registry.put(QUERY_ENHANCE_NODE, new NodeIo(List.of(INPUT_KEY, EVIDENCE, MULTI_TURN_CONTEXT),
				List.of(QUERY_ENHANCE_NODE_OUTPUT)));

		// 外层信封是 SCHEMA_RECALL_NODE_OUTPUT，内层才是下面这两个
		registry.put(SCHEMA_RECALL_NODE, new NodeIo(List.of(QUERY_ENHANCE_NODE_OUTPUT, AGENT_ID),
				List.of(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT)));

		// 唯一一个"Flux + 普通值混合返回"的节点：后三个 key 在 Flux 完成前就已落进 state
		registry.put(TABLE_RELATION_NODE,
				new NodeIo(
						List.of(QUERY_ENHANCE_NODE_OUTPUT, EVIDENCE, TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT,
								COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT, AGENT_ID, SQL_GENERATE_SCHEMA_MISSING_ADVICE),
						List.of(TABLE_RELATION_OUTPUT, GENEGRATED_SEMANTIC_MODEL_PROMPT, DB_DIALECT_TYPE,
								TABLE_RELATION_RETRY_COUNT, TABLE_RELATION_EXCEPTION_OUTPUT)));

		registry.put(FEASIBILITY_ASSESSMENT_NODE,
				new NodeIo(List.of(QUERY_ENHANCE_NODE_OUTPUT, TABLE_RELATION_OUTPUT, EVIDENCE, MULTI_TURN_CONTEXT),
						List.of(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, FINAL_ANSWER)));

		// SQL_GENERATE_OUTPUT 既是输入（上次失败的 SQL）也是输出
		registry.put(SQL_GENERATE_NODE,
				new NodeIo(
						List.of(SQL_GENERATE_COUNT, PLANNER_NODE_OUTPUT, PLAN_CURRENT_STEP, SQL_REGENERATE_REASON,
								SQL_GENERATE_OUTPUT, EVIDENCE, TABLE_RELATION_OUTPUT, QUERY_ENHANCE_NODE_OUTPUT,
								DB_DIALECT_TYPE, SQL_EXECUTE_NODE_OUTPUT),
						List.of(SQL_GENERATE_OUTPUT, SQL_GENERATE_COUNT, SQL_REGENERATE_REASON)));

		// 修复模式下会读回上一版 PLANNER_NODE_OUTPUT 作为 prompt 输入
		registry.put(PLANNER_NODE,
				new NodeIo(
						List.of(IS_ONLY_NL2SQL, QUERY_ENHANCE_NODE_OUTPUT, PLAN_VALIDATION_ERROR,
								GENEGRATED_SEMANTIC_MODEL_PROMPT, TABLE_RELATION_OUTPUT, EVIDENCE, PLANNER_NODE_OUTPUT),
						List.of(PLANNER_NODE_OUTPUT)));

		// 无 LLM 调用、无 Flux：返回普通 Map，key 直接就是 state key
		registry.put(PLAN_EXECUTOR_NODE,
				new NodeIo(
						List.of(PLANNER_NODE_OUTPUT, HUMAN_REVIEW_ENABLED, PLAN_CURRENT_STEP, IS_ONLY_NL2SQL,
								PLAN_REPAIR_COUNT),
						List.of(PLAN_VALIDATION_STATUS, PLAN_VALIDATION_ERROR, PLAN_REPAIR_COUNT, PLAN_NEXT_NODE,
								PLAN_CURRENT_STEP)));

		registry.put(SQL_EXECUTE_NODE,
				new NodeIo(
						List.of(PLAN_CURRENT_STEP, SQL_GENERATE_OUTPUT, AGENT_ID, SQL_EXECUTE_NODE_OUTPUT,
								PLANNER_NODE_OUTPUT, IS_ONLY_NL2SQL, QUERY_ENHANCE_NODE_OUTPUT),
						List.of(SQL_EXECUTE_NODE_OUTPUT, SQL_REGENERATE_REASON, SQL_RESULT_LIST_MEMORY,
								PLAN_CURRENT_STEP, SQL_GENERATE_COUNT)));

		registry.put(PYTHON_GENERATE_NODE,
				new NodeIo(
						List.of(TABLE_RELATION_OUTPUT, SQL_EXECUTE_NODE_OUTPUT, PYTHON_IS_SUCCESS, PYTHON_TRIES_COUNT,
								QUERY_ENHANCE_NODE_OUTPUT, PLANNER_NODE_OUTPUT, PLAN_CURRENT_STEP,
								PYTHON_GENERATE_NODE_OUTPUT, PYTHON_EXECUTE_NODE_OUTPUT),
						List.of(PYTHON_GENERATE_NODE_OUTPUT, PYTHON_TRIES_COUNT)));

		// PYTHON_FALLBACK_MODE 只在"超过重试上限"的降级路径上写
		registry.put(PYTHON_EXECUTE_NODE,
				new NodeIo(List.of(PYTHON_GENERATE_NODE_OUTPUT, SQL_EXECUTE_NODE_OUTPUT, PYTHON_TRIES_COUNT),
						List.of(PYTHON_EXECUTE_NODE_OUTPUT, PYTHON_IS_SUCCESS, PYTHON_FALLBACK_MODE)));

		// 外层信封是 PYTHON_ANALYSIS_NODE_OUTPUT；正常路径与降级路径写的是同一组内层 key
		registry.put(PYTHON_ANALYZE_NODE,
				new NodeIo(
						List.of(QUERY_ENHANCE_NODE_OUTPUT, PYTHON_EXECUTE_NODE_OUTPUT, PLAN_CURRENT_STEP,
								SQL_EXECUTE_NODE_OUTPUT, PYTHON_FALLBACK_MODE),
						List.of(SQL_EXECUTE_NODE_OUTPUT, PLAN_CURRENT_STEP)));

		// 生成报告后会把中间态清空（写 null），这些 key 仍算该节点的输出
		registry.put(REPORT_GENERATOR_NODE,
				new NodeIo(
						List.of(PLANNER_NODE_OUTPUT, QUERY_ENHANCE_NODE_OUTPUT, PLAN_CURRENT_STEP,
								SQL_EXECUTE_NODE_OUTPUT, AGENT_ID),
						List.of(RESULT, SQL_EXECUTE_NODE_OUTPUT, PLAN_CURRENT_STEP, PLANNER_NODE_OUTPUT)));

		registry.put(SEMANTIC_CONSISTENCY_NODE,
				new NodeIo(
						List.of(EVIDENCE, TABLE_RELATION_OUTPUT, DB_DIALECT_TYPE, SQL_GENERATE_OUTPUT,
								QUERY_ENHANCE_NODE_OUTPUT, PLANNER_NODE_OUTPUT, PLAN_CURRENT_STEP),
						List.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, SQL_REGENERATE_REASON)));

		// 无 LLM 调用、无 Flux；HUMAN_NEXT_NODE 是裸字符串 key
		registry.put(HUMAN_FEEDBACK_NODE,
				new NodeIo(List.of(PLAN_REPAIR_COUNT, HUMAN_FEEDBACK_DATA), List.of(HUMAN_NEXT_NODE,
						HUMAN_REVIEW_ENABLED, PLAN_REPAIR_COUNT, PLAN_CURRENT_STEP, PLAN_VALIDATION_ERROR,
						PLANNER_NODE_OUTPUT)));

		return Map.copyOf(registry);
	}

	/**
	 * 返回该 nodeId 声明的输入/输出 key；未登记的 nodeId 返回 {@code null}。
	 */
	public static NodeIo get(String nodeId) {
		return nodeId == null ? null : NODE_IO.get(nodeId);
	}

	/**
	 * 已登记的全部 nodeId。
	 */
	public static Set<String> registeredNodeIds() {
		return NODE_IO.keySet();
	}

	/**
	 * 抽取该节点声明为"输入"的 state 子集，跳过 state 中不存在的 key。
	 */
	public static Map<String, Object> extractInputs(String nodeId, Map<String, Object> state) {
		return extract(nodeId, state, true);
	}

	/**
	 * 抽取该节点声明为"输出"的 state 子集，跳过 state 中不存在的 key。
	 */
	public static Map<String, Object> extractOutputs(String nodeId, Map<String, Object> state) {
		return extract(nodeId, state, false);
	}

	private static Map<String, Object> extract(String nodeId, Map<String, Object> state, boolean inputs) {
		NodeIo nodeIo = get(nodeId);
		if (nodeIo == null || state == null || state.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> extracted = new LinkedHashMap<>();
		for (String key : inputs ? nodeIo.inputKeys() : nodeIo.outputKeys()) {
			if (state.containsKey(key)) {
				extracted.put(key, state.get(key));
			}
		}
		return extracted;
	}

}
