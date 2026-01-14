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
package com.alibaba.cloud.ai.dataagent.vo;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用来记录NL2SQL中间过程的类
 *
 * @author vlsmb
 * @since 2025/8/14
 */
@Data
@AllArgsConstructor
public class Nl2SqlProcessVO {

	/**
	 * 标识过程是否结束
	 */
	@JsonProperty("finished")
	Boolean finished;

	/**
	 * 标识是否成功
	 */
	@JsonProperty("succeed")
	Boolean succeed;

	/**
	 * 当isFinished为true时本字段有效。isSucceed为true则为生成SQL结果，为false则为错误原因
	 */
	@JsonProperty("result")
	String result;

	/**
	 * 当前运行节点名称
	 */
	@JsonProperty("current_node_name")
	String currentNodeName;

	/**
	 * 当前运行节点输出
	 */
	@JsonProperty("current_node_output")
	String currentNodeOutput;

	public static Nl2SqlProcessVO success(String result, String currentNodeName, String currentNodeOutput) {
		return new Nl2SqlProcessVO(true, true, result, currentNodeName, currentNodeOutput);
	}

	public static Nl2SqlProcessVO success(String result) {
		return success(result, StateGraph.END, "");
	}

	public static Nl2SqlProcessVO fail(String reason, String currentNodeName, String currentNodeOutput) {
		return new Nl2SqlProcessVO(true, false, reason, currentNodeName, currentNodeOutput);
	}

	public static Nl2SqlProcessVO fail(String reason) {
		return fail(reason, StateGraph.END, "");
	}

	public static Nl2SqlProcessVO processing(String currentNodeName, String currentNodeOutput) {
		return new Nl2SqlProcessVO(false, false, "", currentNodeName, currentNodeOutput);
	}

}
