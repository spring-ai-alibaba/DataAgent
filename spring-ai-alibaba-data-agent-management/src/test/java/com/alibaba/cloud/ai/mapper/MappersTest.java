/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.mapper;

import com.alibaba.cloud.ai.MySqlContainerConfiguration;
import com.alibaba.cloud.ai.entity.Agent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Mappers 单元测试类
 *
 * @author vlsmb
 * @since 2025/9/26
 */
@MybatisTest
@TestPropertySource(properties = { "spring.sql.init.mode=never" })
@ImportTestcontainers(MySqlContainerConfiguration.class)
@ImportAutoConfiguration(MySqlContainerConfiguration.class)
public class MappersTest {

	@Autowired
	private AgentMapper agentMapper;

	@Test
	public void testAgentMapper() {
		Assertions.assertNotNull(agentMapper);

		agentMapper.findAll().stream().map(Agent::getId).forEach(id -> agentMapper.deleteById(id));

		List<Agent> all = agentMapper.findAll();
		Assertions.assertEquals(List.of(), all);
		Agent agent = Agent.builder()
			.name("test")
			.description("test")
			.avatar("test")
			.status("test")
			.prompt("test")
			.category("test")
			.adminId(1L)
			.tags("test")
			.createTime(LocalDateTime.now().withNano(0))
			.updateTime(LocalDateTime.now().withNano(0))
			.humanReviewEnabled(0)
			.build();
		int insert = agentMapper.insert(agent);
		Assertions.assertEquals(1, insert);
		Agent findById = agentMapper.findById(agent.getId());
		Assertions.assertEquals(agent, findById);
		List<Agent> findByStatus = agentMapper.findByStatus("test");
		Assertions.assertEquals(List.of(agent), findByStatus);
		List<Agent> searchByKeyword = agentMapper.searchByKeyword("test");
		Assertions.assertEquals(List.of(agent), searchByKeyword);
		agent.setName("test2");
		int update = agentMapper.updateById(agent);
		Assertions.assertEquals(1, update);
		agentMapper.deleteById(agent.getId());
		List<Agent> allAfterDelete = agentMapper.findAll();
		Assertions.assertEquals(List.of(), allAfterDelete);
	}

}
