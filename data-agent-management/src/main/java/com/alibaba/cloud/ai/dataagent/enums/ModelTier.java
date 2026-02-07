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
package com.alibaba.cloud.ai.dataagent.enums;

import jakarta.annotation.Nonnull;
import lombok.Getter;

/**
 * 模型能力档位 (Model Capability Tier)
 * <p>
 * 该枚举定义了不同能力层级的模型档位，主要用于区分模型在速度、逻辑能力、工具调用能力等方面的差异。
 * 通过使用不同的档位，系统可以根据具体的业务需求选择最合适的模型配置，以达到最佳的性能和效果。
 */
@Getter
public enum ModelTier {

    /**
     * 极速模型
     * 特点：速度快、成本低、并发高
     * 场景：意图识别、简单摘要、提取字段、心跳检测
     */
    FLASH("FLASH"),

    /**
     * 通用模型
     * 特点：平衡了逻辑与速度，工具调用能力强
     * 场景：日常对话、业务逻辑执行、文案生成
     */
    STANDARD("STANDARD"),

    /**
     * 推理模型
     * 特点：逻辑极强、擅长规划、通常较慢、可能不支持工具调用
     * 场景：复杂问题拆解、代码架构设计、深度分析
     */
    THINKING("THINKING");

    private final String code;

    ModelTier(String code) {
        this.code = code;
    }

    @Nonnull
    public static ModelTier fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("ModelTier code cannot be null or empty");
        }

        for (ModelTier tier : ModelTier.values()) {
            if (tier.code.equalsIgnoreCase(code.trim())) {
                return tier;
            }
        }

        throw new IllegalArgumentException("Unknown ModelTier code: " + code);
    }

}
