package com.alibaba.cloud.ai.dataagent.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 代理配置属性类
 * @author Darlingxxx
 * @since 2026.1.30
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "spring.ai.alibaba.data-agent.ai-proxy")
public class AiProxyProperties {

    /**
     * 是否启用代理，默认为 false
     */
    private boolean enabled = false;

    /**
     * 代理主机地址
     */
    private String host;

    /**
     * 代理端口
     */
    private Integer port;

    /**
     * 代理用户名（可选）
     */
    private String username;

    /**
     * 代理密码（可选）
     */
    private String password;
}
