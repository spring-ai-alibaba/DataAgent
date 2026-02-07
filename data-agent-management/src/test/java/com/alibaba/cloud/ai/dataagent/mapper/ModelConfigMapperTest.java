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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.service.MySqlContainerConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@MybatisTest
@TestPropertySource(properties = { "spring.sql.init.mode=never" })
@ImportTestcontainers(MySqlContainerConfiguration.class)
@ImportAutoConfiguration(MySqlContainerConfiguration.class)
public class ModelConfigMapperTest {

    @Autowired
    private ModelConfigMapper mapper;

    @Test
    @DisplayName("如果存在多个满足条件的模型，应该返回最新的激活模型配置")
    @Sql(statements = {
            "INSERT INTO model_config (provider, base_url, api_key, model_name, is_active, model_type, model_tier) VALUES" +
                    " ('provider', 'https://api', 'key1', 'standard-1', 1, 'CHAT', 'STANDARD')," +
                    " ('provider', 'https://api', 'key2', 'flash', 1, 'CHAT', 'FLASH')," +
                    " ('provider', 'https://api', 'key3', 'standard-2', 1, 'CHAT', 'STANDARD')," +
                    " ('provider', 'https://api', 'key4', 'embedding', 1, 'EMBEDDING', 'STANDARD');"
    })
    void testSelectActiveByTypeAndTier() {
        ModelConfig resultStandard = mapper.selectActiveByTypeAndTier("CHAT", "STANDARD");

        assertThat(resultStandard).isNotNull();
        assertThat(resultStandard.getApiKey()).isEqualTo("key3");
        assertThat(resultStandard.getModelName()).isEqualTo("standard-2");

        ModelConfig resultFlash = mapper.selectActiveByTypeAndTier("CHAT", "FLASH");
        assertThat(resultFlash).isNotNull();
        assertThat(resultFlash.getApiKey()).isEqualTo("key2");
        assertThat(resultFlash.getModelName()).isEqualTo("flash");
    }

    @Test
    @DisplayName("如果根据模型档位查询不到激活模型，应该返回空")
    @Sql(statements = {
            "INSERT INTO model_config (provider, base_url, api_key, model_name, is_active, model_type, model_tier) VALUES" +
                    " ('provider', 'https://api', 'key1', 'standard-1', 1, 'CHAT', 'STANDARD')," +
                    " ('provider', 'https://api', 'key2', 'flash', 1, 'CHAT', 'FLASH')," +
                    " ('provider', 'https://api', 'key3', 'standard-2', 1, 'CHAT', 'STANDARD')," +
                    " ('provider', 'https://api', 'key4', 'embedding', 1, 'EMBEDDING', 'STANDARD');"
    })
    void testSelectActiveByTypeAndTier_FallbackToTypeOnly() {
        ModelConfig resultFlash = mapper.selectActiveByTypeAndTier("CHAT", "THINKING");

        assertThat(resultFlash).isNull();
    }

}
