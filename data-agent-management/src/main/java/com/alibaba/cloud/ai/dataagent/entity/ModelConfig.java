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
package com.alibaba.cloud.ai.dataagent.entity;

import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModelConfig {

	private Integer id;

	// 厂商标识 (方便前端展示回显，实际调用主要靠 baseUrl)
	private String provider;

	// 关键配置
	private String baseUrl;

	private String apiKey;

	private String modelName;

	private Double temperature;

	private Boolean isActive = false;

	private Integer maxTokens;

	// 模型类型
	// 可选值："CHAT", "EMBEDDING"
	private ModelType modelType;

	private String completionsPath;

	private String embeddingsPath;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

	// 0=未删除, 1=已删除
	private Integer isDeleted;

}
