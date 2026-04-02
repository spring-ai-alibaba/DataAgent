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
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.common.TestFixtures;
import com.alibaba.cloud.ai.dataagent.dto.prompt.IntentRecognitionOutputDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntentRecognitionDispatcherTest {

	private IntentRecognitionDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		dispatcher = new IntentRecognitionDispatcher();
	}

	@Test
	void apply_dataAnalysisIntent_routesToEvidenceRecall() throws Exception {
		OverAllState state = new OverAllState();
		IntentRecognitionOutputDTO dto = TestFixtures.createIntentDTO("《可能的数据分析请求》");
		state.updateState(Map.of(INTENT_RECOGNITION_NODE_OUTPUT, dto));

		assertEquals(EVIDENCE_RECALL_NODE, dispatcher.apply(state));
	}

	@Test
	void apply_chatIntent_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		IntentRecognitionOutputDTO dto = TestFixtures.createIntentDTO("《闲聊或无关指令》");
		state.updateState(Map.of(INTENT_RECOGNITION_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_nullClassification_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		IntentRecognitionOutputDTO dto = new IntentRecognitionOutputDTO();
		dto.setClassification(null);
		state.updateState(Map.of(INTENT_RECOGNITION_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_emptyClassification_routesToEnd() throws Exception {
		OverAllState state = new OverAllState();
		IntentRecognitionOutputDTO dto = TestFixtures.createIntentDTO("   ");
		state.updateState(Map.of(INTENT_RECOGNITION_NODE_OUTPUT, dto));

		assertEquals(END, dispatcher.apply(state));
	}

	@Test
	void apply_missingOutput_throwsException() {
		OverAllState state = new OverAllState();

		assertThrows(IllegalStateException.class, () -> dispatcher.apply(state));
	}

}
