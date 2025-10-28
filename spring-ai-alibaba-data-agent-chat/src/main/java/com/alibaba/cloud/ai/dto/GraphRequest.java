package com.alibaba.cloud.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GraphRequest {

	private String agentId;

	private String threadId;

	private String query;

	private boolean humanFeedback;

	private String humanFeedbackContent;

	private boolean rejectedPlan;

	private boolean nl2sqlOnly;

	private boolean plainReport;

}
