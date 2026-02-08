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
package com.alibaba.cloud.ai.dataagent.tools;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

@Slf4j
@AllArgsConstructor
@Component
public class SequentialThinkingTool implements BiFunction<SequentialThinkingTool.Request, ToolContext, String> {

	private final List<Request> thoughtHistory = new ArrayList<>();

	private final Map<String, List<Request>> branches = new HashMap<>();

	@Override
	public String apply(Request request, ToolContext toolContext) {
		log.info("========== Sequential Thinking Tool Start ==========");

		try {
			// Validate input
			if (!validate(request)) {
				return "Validation failed: Invalid input parameters";
			}


			// Adjust totalThoughts if thoughtNumber exceeds it
			if (request.thoughtNumber() > request.totalThoughts()) {
				request = new Request(request.thought(), request.nextThoughtNeeded(), request.thoughtNumber(),
						request.thoughtNumber(), // Update totalThoughts
						request.isRevision(), request.revisesThought(), request.branchFromThought(), request.branchId(),
						request.needsMoreThoughts());
			}

			// Add to thought history
			thoughtHistory.add(request);

			// Handle branching
			if (request.branchFromThought() != null && request.branchId() != null && !request.branchId().isEmpty()) {
				branches.computeIfAbsent(request.branchId(), k -> new ArrayList<>()).add(request);
			}

			log.info("thinking Thought: {}", request.thought());

			// Prepare response data
			List<String> branchKeys = new ArrayList<>(branches.keySet());
			boolean incomplete = request.nextThoughtNeeded()
					|| (request.needsMoreThoughts() != null && request.needsMoreThoughts())
					|| request.thoughtNumber() < request.totalThoughts();

			Map<String, Object> responseData = new HashMap<>();
			responseData.put("thought_number", request.thoughtNumber());
			responseData.put("total_thoughts", request.totalThoughts());
			responseData.put("next_thought_needed", request.nextThoughtNeeded());
			responseData.put("branches", branchKeys);
			responseData.put("thought_history_length", thoughtHistory.size());
			responseData.put("display_type", "thinking");
			responseData.put("thought", request.thought());
			responseData.put("incomplete_steps", incomplete);

			log.info("thinking Execute completed - Thought {}/{}", request.thoughtNumber(),
					request.totalThoughts());

			String outputMsg = "Thought process recorded";
			if (incomplete) {
				outputMsg = "Thought process recorded - unfinished steps remain, continue exploring and calling tools";
			}

			log.info("========== Sequential Thinking Tool End ==========");
			return outputMsg;
		}
		catch (Exception e) {
			log.error("Error in sequential thinking tool", e);
			return "Error in sequential thinking tool: " + e.getMessage();
		}
	}

	public ToolCallback toolCallback() {
		return FunctionToolCallback.builder("sequential_thinking", this)
			.description(
					"""
							A detailed tool for dynamic and reflective problem-solving through thoughts.
							This tool helps analyze problems through a flexible thinking process that can adapt and evolve.
							Each thought can build on, question, or revise previous insights as understanding deepens.
							## When to Use This Tool
							- Breaking down complex problems into steps
							- Planning and design with room for revision
							- Analysis that might need course correction
							- Problems where the full scope might not be clear initially
							- Problems that require a multi-step solution
							- Tasks that need to maintain context over multiple steps
							- Situations where irrelevant information needs to be filtered out
							## Typical Application Scenarios
							This tool is particularly useful for data analysis workflows involving:
							- Understanding user business intent from natural language queries
							- Identifying relevant database tables and columns for the business scenario
							- Planning SQL query structure based on data requirements
							- Analyzing query results and extracting meaningful insights
							- Iterating on SQL statements when results don't meet expectations
							- Combining multiple data sources to answer complex questions
							- Validating data quality and completeness before drawing conclusions
							- Explaining analytical findings in business-friendly language
							## Key Features
							- You can adjust total_thoughts up or down as you progress
							- You can question or revise previous thoughts
							- You can add more thoughts even after reaching what seemed like the end
							- You can express uncertainty and explore alternative approaches
							- Not every thought needs to build linearly - you can branch or backtrack
							- Generates a solution hypothesis
							- Verifies the hypothesis based on the Chain of Thought steps
							- Repeats the process until satisfied
							- Provides a correct answer
							## Parameters Explained
							- **thought**: Your current thinking step, which can include:
							  * Regular analytical steps
							  * Revisions of previous thoughts
							  * Questions about previous decisions
							  * Realizations about needing more analysis
							  * Changes in approach
							  * Hypothesis generation
							  * Hypothesis verification

							  **CRITICAL - User-Friendly Thinking**: Write your thoughts in natural, user-friendly language. NEVER mention tool names (like "grep_chunks", "knowledge_search", "web_search", etc.) in your thinking process. Instead, describe your actions in plain language:
							  - ❌ BAD: "I'll use list_table_schema to search for keywords, then sql_execute for semantic understanding"
							  - ✅ GOOD: "I'll start by searching for key terms in the knowledge, then explore related concepts"
							  - ❌ BAD: "After list_table_schema returns results, I'll use sql_execute"
							  - ✅ GOOD: "After finding relevant documents, I'll search for semantically related content"

							  Write thinking as if explaining your reasoning to a user, not documenting technical steps. Focus on WHAT you're trying to find and WHY, not HOW (which tools you'll use).
							- **next_thought_needed**: True if you need more thinking, even if at what seemed like the end
							- **thought_number**: Current number in sequence (can go beyond initial total if needed)
							- **total_thoughts**: Current estimate of thoughts needed (can be adjusted up/down)
							- **is_revision**: A boolean indicating if this thought revises previous thinking
							- **revises_thought**: If is_revision is true, which thought number is being reconsidered
							- **branch_from_thought**: If branching, which thought number is the branching point
							- **branch_id**: Identifier for the current branch (if any)
							- **needs_more_thoughts**: If reaching end but realizing more thoughts needed
							""")
			.inputType(Request.class)
			.build();
	}

	@JsonClassDescription("Request for sequential thinking tool")
	public record Request(@JsonProperty(value = "thought",
			required = true) @JsonPropertyDescription("Your current thinking step. Write in natural, user-friendly language. NEVER mention tool names (like \"list_table_schema\", \"sql_execute\", \"web_search\", etc.). Instead, describe actions in plain language (e.g., \"I'll search for key terms\" instead of \"I'll use list_table_schema\"). Focus on WHAT you're trying to find and WHY, not HOW (which tools you'll use).") String thought,

			@JsonProperty(value = "next_thought_needed",
					required = true) @JsonPropertyDescription("Whether another thought step is needed") boolean nextThoughtNeeded,

			@JsonProperty(value = "thought_number",
					required = true) @JsonPropertyDescription("Current thought number (numeric value, e.g., 1, 2, 3)") int thoughtNumber,

			@JsonProperty(value = "total_thoughts",
					required = true) @JsonPropertyDescription("Estimated total thoughts needed (numeric value, e.g., 5, 10)") int totalThoughts,

			@JsonProperty(
					value = "is_revision") @JsonPropertyDescription("Whether this revises previous thinking") Boolean isRevision,

			@JsonProperty(
					value = "revises_thought") @JsonPropertyDescription("Which thought is being reconsidered") Integer revisesThought,

			@JsonProperty(
					value = "branch_from_thought") @JsonPropertyDescription("Branching point thought number") Integer branchFromThought,

			@JsonProperty(value = "branch_id") @JsonPropertyDescription("Branch identifier") String branchId,

			@JsonProperty(
					value = "needs_more_thoughts") @JsonPropertyDescription("If more thoughts are needed") Boolean needsMoreThoughts) {

	}

	private boolean validate(Request request) {
		// Validate thought (required)
		if (request.thought() == null || request.thought().isEmpty()) {
			log.error("Invalid thought: must be a non-empty string");
			return false;
		}

		// Validate thoughtNumber (required)
		if (request.thoughtNumber() < 1) {
			log.error("Invalid thoughtNumber: must be >= 1");
			return false;
		}
		// Validate totalThoughts (required)
		if (request.totalThoughts() < 1) {
			log.error("Invalid totalThoughts: must be >= 1");
			return false;
		}
		return true;
	}
}
