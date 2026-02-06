package com.alibaba.cloud.ai.dataagent.enums;

import jakarta.annotation.Nonnull;
import lombok.Getter;

/**
 * 模型规格枚举
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
