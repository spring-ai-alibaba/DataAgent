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

import com.alibaba.cloud.ai.dataagent.service.langfuse.NodeIoRegistry.NodeIo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link NodeIoRegistry} 的白名单正确性测试。
 *
 * <p>
 * 除常规单测外，本类包含两条<b>源码级断言</b>：直接解析 {@code DataAgentConfiguration.java} 与各
 * {@code *Node.java} 源文件，把"人工核对白名单是否登记对了"变成自动化检查。这样后续新增节点或改动 output key 时，遗漏会在构建期暴露，而不是等到
 * Langfuse 面板上出现莫名的红色 span 才被发现。
 *
 * @author suke
 */
class NodeIoRegistryTest {

	private static final Path MAIN_JAVA = Path.of("src/main/java/com/alibaba/cloud/ai/dataagent");

	private static final Path CONFIG_FILE = MAIN_JAVA.resolve("config/DataAgentConfiguration.java");

	private static final Path NODE_DIR = MAIN_JAVA.resolve("workflow/node");

	/** {@code addNode(NODE_ID, ...)} 里的第一个参数 */
	private static final Pattern ADD_NODE = Pattern.compile("\\.addNode\\(\\s*([A-Z_][A-Z0-9_]*)\\s*,");

	/** {@code keyStrategyHashMap.put(KEY, ...)} 里的第一个参数 */
	private static final Pattern KEY_STRATEGY = Pattern
		.compile("keyStrategyHashMap\\.put\\(\\s*([A-Z_][A-Z0-9_]*)\\s*,");

	@Test
	void everyRegisteredNodeIdHasNonEmptyIo() {
		assertFalse(NodeIoRegistry.registeredNodeIds().isEmpty(), "registry must not be empty");

		for (String nodeId : NodeIoRegistry.registeredNodeIds()) {
			NodeIo nodeIo = NodeIoRegistry.get(nodeId);
			assertNotNull(nodeIo, () -> nodeId + " must have an entry");
			assertFalse(nodeIo.inputKeys().isEmpty(), () -> nodeId + " must declare at least one input key");
			assertFalse(nodeIo.outputKeys().isEmpty(), () -> nodeId + " must declare at least one output key");
		}
	}

	@Test
	void noDuplicateKeysWithinASingleNode() {
		for (String nodeId : NodeIoRegistry.registeredNodeIds()) {
			NodeIo nodeIo = NodeIoRegistry.get(nodeId);
			assertEquals(new HashSet<>(nodeIo.inputKeys()).size(), nodeIo.inputKeys().size(),
					() -> nodeId + " has duplicate input keys");
			assertEquals(new HashSet<>(nodeIo.outputKeys()).size(), nodeIo.outputKeys().size(),
					() -> nodeId + " has duplicate output keys");
		}
	}

	/**
	 * 覆盖断言：{@code DataAgentConfiguration} 里 {@code addNode} 注册的每个 nodeId 都必须登记。
	 * 直接解析源码而非硬编码 16 这个数字，这样新增节点时本测试会自动失败。
	 */
	@Test
	void registryCoversEveryAddNodeRegistration() throws IOException {
		Set<String> addNodeIds = matchAll(read(CONFIG_FILE), ADD_NODE);

		assertFalse(addNodeIds.isEmpty(), "failed to parse addNode(...) calls — did the config file move?");

		Set<String> missing = new HashSet<>(addNodeIds);
		missing.removeAll(constantNamesOf(NodeIoRegistry.registeredNodeIds()));
		assertTrue(missing.isEmpty(), () -> "node(s) registered via addNode but missing from NodeIoRegistry: " + missing
				+ " — a missing entry means that node gets no Langfuse span at all");

		Set<String> extra = new HashSet<>(constantNamesOf(NodeIoRegistry.registeredNodeIds()));
		extra.removeAll(addNodeIds);
		assertTrue(extra.isEmpty(), () -> "NodeIoRegistry declares node(s) that are not in the graph: " + extra);
	}

	/**
	 * 约束：output 白名单不得登记纯 Flux 信封标签。
	 *
	 * <p>
	 * 信封标签（如 {@code SCHEMA_RECALL_NODE_OUTPUT}）永远不会进入 state。若误登记，该节点的 span 会因"声明的 output
	 * key 一个都不存在"而<b>每次执行都被误判为失败</b>。
	 */
	@Test
	void noNodeDeclaresAFluxEnvelopeKeyAsOutput() {
		for (String nodeId : NodeIoRegistry.registeredNodeIds()) {
			for (String outputKey : NodeIoRegistry.get(nodeId).outputKeys()) {
				assertFalse(NodeIoRegistry.FLUX_ENVELOPE_KEYS.contains(outputKey),
						() -> nodeId + " declares Flux-envelope key '" + outputKey
								+ "' as an output; envelope keys never enter state, so this node would always "
								+ "be judged as failed. Declare the inner resultSupplier keys instead.");
			}
		}
	}

	/**
	 * 源码级断言：每个节点的 output 白名单必须与该节点源码中实际写入的 key 集合一致（白名单 ⊆ 源码所提及的 key）。
	 *
	 * <p>
	 * 这条断言抓的是"白名单写了一个该节点根本不会产出的 key"这类笔误——那会削弱失败判定的准确性（多一个永不出现的 key
	 * 不会造成误判，但少一个会）。反方向（源码有、白名单没有）无法靠正则可靠判定，因为节点会读写大量非输出用途的 key， 故此处只做单向检查，配合
	 * {@link #noNodeDeclaresAFluxEnvelopeKeyAsOutput} 与人工核对。
	 */
	@Test
	void declaredOutputKeysAppearInTheNodeSourceFile() throws IOException {
		Map<String, String> nodeSources = readNodeSources();

		for (String nodeId : NodeIoRegistry.registeredNodeIds()) {
			String source = nodeSources.get(nodeId);
			assertNotNull(source, () -> "no source file found for " + nodeId);

			for (String outputKey : NodeIoRegistry.get(nodeId).outputKeys()) {
				// 白名单存的是常量的“值”，而源码里写的是常量“名”，两者并不总是相同
				// （如 FINAL_ANSWER = "final_answer"），故先反查常量名再比对。
				String needle = sourceTokenFor(outputKey);
				assertTrue(source.contains(needle), () -> nodeId + " declares output key '" + outputKey
						+ "' (source token '" + needle + "') but that token never appears in its source file");
			}
		}
	}

	/**
	 * 记录一个反直觉的事实，防止后续维护者把"已注册 KeyStrategy"当作"该 key 会进入 state"的证据。
	 *
	 * <p>
	 * 设计初稿曾打算用"output key 必须已注册 KeyStrategy"来自动识别信封标签，依据是当时观察到
	 * {@code SCHEMA_RECALL_NODE_OUTPUT} 是唯一未注册的 {@code *_OUTPUT} 常量。但
	 * {@code PYTHON_ANALYSIS_NODE_OUTPUT} 同样是纯信封标签，<b>却注册了</b> KeyStrategy——该判据因此不成立。
	 * 本测试把这个反例固化下来。
	 */
	@Test
	void keyStrategyRegistrationDoesNotImplyTheKeyEntersState() throws IOException {
		Set<String> registeredStrategies = matchAll(read(CONFIG_FILE), KEY_STRATEGY);

		assertTrue(registeredStrategies.contains("PYTHON_ANALYSIS_NODE_OUTPUT"),
				"PYTHON_ANALYSIS_NODE_OUTPUT is expected to have a KeyStrategy registered");
		assertTrue(NodeIoRegistry.FLUX_ENVELOPE_KEYS.contains(PYTHON_ANALYSIS_NODE_OUTPUT),
				"PYTHON_ANALYSIS_NODE_OUTPUT is nevertheless a pure Flux envelope");

		assertFalse(registeredStrategies.contains("SCHEMA_RECALL_NODE_OUTPUT"),
				"SCHEMA_RECALL_NODE_OUTPUT is expected to have no KeyStrategy");
		assertTrue(NodeIoRegistry.FLUX_ENVELOPE_KEYS.contains(SCHEMA_RECALL_NODE_OUTPUT));
	}

	@Test
	void schemaRecallDeclaresInnerKeysNotTheEnvelope() {
		List<String> outputs = NodeIoRegistry.get(SCHEMA_RECALL_NODE).outputKeys();

		assertEquals(List.of(TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT, COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT), outputs);
	}

	@Test
	void pythonAnalyzeDeclaresInnerKeysNotTheEnvelope() {
		List<String> outputs = NodeIoRegistry.get(PYTHON_ANALYZE_NODE).outputKeys();

		assertEquals(List.of(SQL_EXECUTE_NODE_OUTPUT, PLAN_CURRENT_STEP), outputs);
	}

	@Test
	void tableRelationDeclaresBothImmediateAndDeferredOutputs() {
		List<String> outputs = NodeIoRegistry.get(TABLE_RELATION_NODE).outputKeys();

		// 延迟写入（Flux 完成后由 resultMap 产出）
		assertTrue(outputs.contains(TABLE_RELATION_OUTPUT));
		assertTrue(outputs.contains(GENEGRATED_SEMANTIC_MODEL_PROMPT));
		// 立即写入（与 Flux 并列在外层 Map 中返回）
		assertTrue(outputs.contains(DB_DIALECT_TYPE));
		assertTrue(outputs.contains(TABLE_RELATION_RETRY_COUNT));
		assertTrue(outputs.contains(TABLE_RELATION_EXCEPTION_OUTPUT));
	}

	@Test
	void degradedAndValidationPathKeysAreDeclared() {
		// PythonExecuteNode 的降级路径写 PYTHON_FALLBACK_MODE
		assertTrue(NodeIoRegistry.get(PYTHON_EXECUTE_NODE).outputKeys().contains(PYTHON_FALLBACK_MODE));
		// PlanExecutorNode 校验失败路径只写这两个，不写 PLAN_NEXT_NODE
		assertTrue(NodeIoRegistry.get(PLAN_EXECUTOR_NODE).outputKeys().contains(PLAN_VALIDATION_STATUS));
		assertTrue(NodeIoRegistry.get(PLAN_EXECUTOR_NODE).outputKeys().contains(PLAN_VALIDATION_ERROR));
		// 闲聊/澄清路径写 FINAL_ANSWER
		assertTrue(NodeIoRegistry.get(INTENT_RECOGNITION_NODE).outputKeys().contains(FINAL_ANSWER));
		assertTrue(NodeIoRegistry.get(FEASIBILITY_ASSESSMENT_NODE).outputKeys().contains(FINAL_ANSWER));
	}

	@Test
	void get_returnsNullForUnknownOrNullNodeId() {
		assertNull(NodeIoRegistry.get("NO_SUCH_NODE"));
		assertNull(NodeIoRegistry.get(null));
	}

	@Test
	void extractInputs_picksOnlyDeclaredKeysPresentInState() {
		Map<String, Object> state = new LinkedHashMap<>();
		state.put(INPUT_KEY, "查一下销量");
		state.put(MULTI_TURN_CONTEXT, "(无)");
		// 未被 INTENT_RECOGNITION_NODE 声明为输入，必须被过滤掉
		state.put(SQL_GENERATE_OUTPUT, "SELECT 1");

		Map<String, Object> inputs = NodeIoRegistry.extractInputs(INTENT_RECOGNITION_NODE, state);

		assertEquals(Map.of(INPUT_KEY, "查一下销量", MULTI_TURN_CONTEXT, "(无)"), inputs);
	}

	@Test
	void extractOutputs_skipsDeclaredKeysAbsentFromState() {
		Map<String, Object> state = Map.of(INTENT_RECOGNITION_NODE_OUTPUT, "DATA_ANALYSIS");

		Map<String, Object> outputs = NodeIoRegistry.extractOutputs(INTENT_RECOGNITION_NODE, state);

		// FINAL_ANSWER 也被声明，但本次不在 state 中
		assertEquals(Map.of(INTENT_RECOGNITION_NODE_OUTPUT, "DATA_ANALYSIS"), outputs);
	}

	@Test
	void extractOutputs_retainsKeysExplicitlySetToNull() {
		// ReportGeneratorNode 会把中间态显式写成 null；这些 key 仍算"存在"
		Map<String, Object> state = new HashMap<>();
		state.put(RESULT, "报告正文");
		state.put(PLANNER_NODE_OUTPUT, null);

		Map<String, Object> outputs = NodeIoRegistry.extractOutputs(REPORT_GENERATOR_NODE, state);

		assertTrue(outputs.containsKey(PLANNER_NODE_OUTPUT),
				"a key explicitly set to null must still count as present");
		assertNull(outputs.get(PLANNER_NODE_OUTPUT));
	}

	@Test
	void extract_returnsEmptyForUnknownNodeOrEmptyState() {
		assertTrue(NodeIoRegistry.extractInputs("NO_SUCH_NODE", Map.of(INPUT_KEY, "x")).isEmpty());
		assertTrue(NodeIoRegistry.extractOutputs("NO_SUCH_NODE", Map.of(INPUT_KEY, "x")).isEmpty());
		assertTrue(NodeIoRegistry.extractInputs(INTENT_RECOGNITION_NODE, Map.of()).isEmpty());
		assertTrue(NodeIoRegistry.extractOutputs(INTENT_RECOGNITION_NODE, null).isEmpty());
	}

	// --- helpers ---

	private static String read(Path path) throws IOException {
		assertTrue(Files.exists(path), () -> "expected source file at " + path.toAbsolutePath()
				+ " — run this test from the data-agent-management module directory");
		return Files.readString(path);
	}

	private static Set<String> matchAll(String source, Pattern pattern) {
		Set<String> found = new HashSet<>();
		Matcher matcher = pattern.matcher(source);
		while (matcher.find()) {
			found.add(matcher.group(1));
		}
		return found;
	}

	/**
	 * nodeId 常量的<i>值</i>与其<i>名字</i>在本项目中恒等（如
	 * {@code INTENT_RECOGNITION_NODE = "INTENT_RECOGNITION_NODE"}），源码解析拿到的是名字，故可直接比对。
	 */
	private static Set<String> constantNamesOf(Set<String> nodeIds) {
		return new HashSet<>(nodeIds);
	}

	/**
	 * 把每个 nodeId 映射到对应 {@code *Node.java} 的源码文本。映射依据：节点类名去掉 {@code Node} 后缀的下划线大写形式即
	 * nodeId（{@code PythonAnalyzeNode} → {@code PYTHON_ANALYZE_NODE}）。
	 */
	private static Map<String, String> readNodeSources() throws IOException {
		assertTrue(Files.isDirectory(NODE_DIR), () -> "expected node directory at " + NODE_DIR.toAbsolutePath());

		Map<String, String> byNodeId = new HashMap<>();
		try (var paths = Files.list(NODE_DIR)) {
			for (Path path : paths.filter(p -> p.getFileName().toString().endsWith("Node.java")).toList()) {
				String className = path.getFileName().toString().replace(".java", "");
				byNodeId.put(toNodeId(className), Files.readString(path));
			}
		}
		return byNodeId;
	}

	private static String toNodeId(String className) {
		String snake = className.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
		// PythonAnalyzeNode → PYTHON_ANALYZE_NODE；SqlGenerateNode → SQL_GENERATE_NODE
		return snake;
	}

	/**
	 * 把一个 state key 的<i>值</i>翻译成它在节点源码中出现的形式。
	 *
	 * <p>
	 * 多数 {@code Constant} 的名与值相同（{@code EVIDENCE = "EVIDENCE"}），但并非全部
	 * （{@code FINAL_ANSWER = "final_answer"}、{@code RESULT = "result"}）。节点源码引用的是常量名，
	 * 故此处反射 {@code Constant} 找出该值对应的常量名；找不到则说明源码里用的是裸字符串字面量。
	 */
	private static String sourceTokenFor(String keyValue) {
		for (java.lang.reflect.Field field : com.alibaba.cloud.ai.dataagent.constant.Constant.class.getFields()) {
			try {
				if (field.getType() == String.class && keyValue.equals(field.get(null))) {
					return field.getName();
				}
			}
			catch (IllegalAccessException ignored) {
				// 常量均为 public static final，正常不会到这里
			}
		}
		return "\"" + keyValue + "\"";
	}

}
