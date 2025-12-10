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

package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.MySqlContainerConfiguration;
import com.alibaba.cloud.ai.dataagent.entity.*;
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

	@Autowired
	private AgentKnowledgeMapper agentKnowledgeMapper;

	@Autowired
	private AgentPresetQuestionMapper agentPresetQuestionMapper;

	@Autowired
	private ChatSessionMapper chatSessionMapper;

	@Autowired
	private ChatMessageMapper chatMessageMapper;

	@Autowired
	private SemanticModelMapper semanticModelMapper;

	@Autowired
	private BusinessKnowledgeMapper businessKnowledgeMapper;

	private Long createAgent(String name) {
		Agent agent = Agent.builder()
			.name(name)
			.description("for fk")
			.avatar("a")
			.status("draft")
			.prompt("p")
			.category("c")
			.adminId(1L)
			.tags("t")
			.createTime(LocalDateTime.now().withNano(0))
			.updateTime(LocalDateTime.now().withNano(0))
			.humanReviewEnabled(0)
			.build();
		agentMapper.insert(agent);
		return agent.getId();
	}

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

	@Test
	public void testChatSessionAndMessageCrud() {
		Long agentId = createAgent("session-holder");
		String sessionId = java.util.UUID.randomUUID().toString();
		// insert session
		ChatSession session = new ChatSession(sessionId, agentId.intValue(), "tc_session", "active", 1L);
		session.setCreateTime(LocalDateTime.now());
		session.setUpdateTime(LocalDateTime.now());
		int ins = chatSessionMapper.insert(session);
		Assertions.assertEquals(1, ins);

		// insert message
		ChatMessage msg = new ChatMessage();
		msg.setSessionId(sessionId);
		msg.setRole("user");
		msg.setContent("hello tc");
		msg.setMessageType("text");
		int mins = chatMessageMapper.insert(msg);
		Assertions.assertEquals(1, mins);

		// read
		List<ChatMessage> list = chatMessageMapper.selectBySessionId(sessionId);
		Assertions.assertEquals(1, list.size());
		int cnt = chatMessageMapper.countBySessionId(sessionId);
		Assertions.assertEquals(1, cnt);

		// update session
		chatSessionMapper.updateTitle(sessionId, "tc_session_updated", LocalDateTime.now());
		chatSessionMapper.updatePinStatus(sessionId, true, LocalDateTime.now());
		chatSessionMapper.updateSessionTime(sessionId, LocalDateTime.now());

		// delete
		int md = chatMessageMapper.deleteById(list.get(0).getId());
		Assertions.assertEquals(1, md);
		int sd = chatSessionMapper.softDeleteById(sessionId, LocalDateTime.now());
		Assertions.assertEquals(1, sd);

		// cleanup agent
		agentMapper.deleteById(agentId);
	}

	@Test
	public void testSemanticModelCrud() {
		Long agentId = createAgent("semantic-holder");
		SemanticModel m = new SemanticModel();
		m.setAgentId(Math.toIntExact(agentId));
		m.setDatasourceId(1); // 添加数据源ID
		m.setTableName("test_table"); // 添加表名
		m.setColumnName("origin_tc");
		m.setBusinessName("显示名");
		m.setSynonyms("别名A,别名B");
		m.setBusinessDescription("desc");
		m.setColumnComment("字段注释"); // 添加字段注释
		m.setDataType("VARCHAR");
		m.setStatus(1);
		int ins = semanticModelMapper.insert(m);
		Assertions.assertEquals(1, ins);
		Assertions.assertNotNull(m.getId());

		List<SemanticModel> query = semanticModelMapper.selectByAgentId(Long.valueOf(m.getAgentId()));
		Assertions.assertTrue(query.stream().anyMatch(x -> x.getId().equals(m.getId())));

		semanticModelMapper.disableById(m.getId());
		semanticModelMapper.enableById(m.getId());

		m.setBusinessDescription("desc2");
		int upd = semanticModelMapper.updateById(m);
		Assertions.assertEquals(1, upd);

		int del = semanticModelMapper.deleteById(m.getId());
		Assertions.assertEquals(1, del);

		agentMapper.deleteById(agentId);
	}

	@Test
	public void testAgentPresetQuestionCrud() {
		// 先创建一个合法的 Agent 以满足外键约束
		Long agentId = createAgent("preset-holder");

		// clean existing
		agentPresetQuestionMapper.deleteByAgentId(agentId);

		AgentPresetQuestion q = new AgentPresetQuestion();
		q.setAgentId(agentId);
		q.setQuestion("q1");
		q.setSortOrder(0);
		q.setIsActive(true);
		int ins = agentPresetQuestionMapper.insert(q);
		Assertions.assertEquals(1, ins);

		List<AgentPresetQuestion> qs = agentPresetQuestionMapper.selectByAgentId(agentId);
		Assertions.assertEquals(1, qs.size());

		q.setQuestion("q1_updated");
		int upd = agentPresetQuestionMapper.update(q);
		Assertions.assertEquals(1, upd);

		int del = agentPresetQuestionMapper.deleteById(qs.get(0).getId());
		Assertions.assertEquals(1, del);

		// 清理创建的 agent
		agentMapper.deleteById(agentId);
	}

	@Test
	public void testBusinessKnowledgeMapperCrud() {
		Long agentId = createAgent("bk-holder");
		// clean
		businessKnowledgeMapper.selectAll().forEach(b -> businessKnowledgeMapper.deleteById(b.getId()));

		List<BusinessKnowledge> before = businessKnowledgeMapper.selectAll();
		Assertions.assertEquals(List.of(), before);

		BusinessKnowledge k = new BusinessKnowledge();
		k.setBusinessTerm("term_ut");
		k.setDescription("desc_ut");
		k.setSynonyms("a,b");
		k.setAgentId(agentId);
		try {
			java.lang.reflect.Field field = BusinessKnowledge.class.getDeclaredField("isRecall");
			field.setAccessible(true);
			field.set(k, 1);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		int ins = businessKnowledgeMapper.insert(k);
		Assertions.assertEquals(1, ins);
		Assertions.assertNotNull(k.getId());

		List<BusinessKnowledge> byDataset = businessKnowledgeMapper.selectByAgentId(agentId);
		Assertions.assertEquals(1, byDataset.size());

		List<BusinessKnowledge> search = businessKnowledgeMapper.searchInAgent(agentId, "term_ut");
		Assertions.assertTrue(search.stream().anyMatch(x -> x.getId().equals(k.getId())));

		k.setDescription("desc_ut_updated");
		int upd = businessKnowledgeMapper.updateById(k);
		Assertions.assertEquals(1, upd);

		int del = businessKnowledgeMapper.deleteById(k.getId());
		Assertions.assertEquals(1, del);
		agentMapper.deleteById(agentId);
	}

}
