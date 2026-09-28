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
package com.alibaba.cloud.ai.dataagent.service.graph.turn;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TurnMemorySnapshotTest {

	@Test
	void finalTextAloneIsNotVerifiedEvidence() {
		TurnMemorySnapshot snapshot = new TurnMemorySnapshot();
		OverAllState state = emptyState();
		doReturn(Optional.of("please configure a datasource")).when(state).value(FINAL_ANSWER);

		snapshot.capture(state, "QueryEnhanceNode");

		assertThat(snapshot.hasVerifiedEvidence()).isFalse();
	}

	@Test
	void executedResultMakesSuccessfulTurnMemoryEligible() {
		TurnMemorySnapshot snapshot = new TurnMemorySnapshot();
		OverAllState state = emptyState();
		doReturn(Optional.of(Map.of("rows", List.of(Map.of("revenue", 100))))).when(state)
			.value(SQL_EXECUTE_NODE_OUTPUT);

		snapshot.capture(state, "SqlExecuteNode");

		assertThat(snapshot.hasVerifiedEvidence()).isTrue();
		assertThat(snapshot.resultArtifactJson()).contains("\"revenue\":100");
	}

	@Test
	void generatedReportWithoutExecutionResultIsNotVerifiedEvidence() {
		TurnMemorySnapshot snapshot = new TurnMemorySnapshot();
		OverAllState state = emptyState();
		doReturn(Optional.of("看起来收入增长了 20%")).when(state).value(FINAL_ANSWER);

		snapshot.capture(state, "ReportGeneratorNode");

		assertThat(snapshot.hasVerifiedEvidence()).isFalse();
	}

	private OverAllState emptyState() {
		OverAllState state = mock(OverAllState.class);
		doReturn(Optional.empty()).when(state).value(anyString());
		return state;
	}

}
