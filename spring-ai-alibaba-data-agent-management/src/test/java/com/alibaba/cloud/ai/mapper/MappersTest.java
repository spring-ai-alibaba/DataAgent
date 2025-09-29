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
import com.alibaba.cloud.ai.entity.AgentKnowledge;
import com.alibaba.cloud.ai.entity.AgentPresetQuestion;
import com.alibaba.cloud.ai.entity.ChatMessage;
import com.alibaba.cloud.ai.entity.ChatSession;
import com.alibaba.cloud.ai.entity.SemanticModel;
import com.alibaba.cloud.ai.entity.BusinessKnowledge;
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
		String sessionId = java.util.UUID.randomUUID().toString();
		// insert session
		ChatSession session = new ChatSession(sessionId, 1, "tc_session", "active", 1L);
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
	}

	@Test
	public void testSemanticModelCrud() {
		// clean none (isolated by generated id)
		SemanticModel m = new SemanticModel();
		m.setAgentId(1L);
		m.setOriginalFieldName("origin_tc");
		m.setAgentFieldName("显示名");
		m.setFieldSynonyms("别名A,别名B");
		m.setFieldDescription("desc");
		m.setFieldType("VARCHAR");
		m.setOriginalDescription("origin");
		m.setDefaultRecall(true);
		m.setEnabled(true);
		int ins = semanticModelMapper.insert(m);
		Assertions.assertEquals(1, ins);
		Assertions.assertNotNull(m.getId());

		List<SemanticModel> query = semanticModelMapper.selectByAgentId(m.getAgentId());
		Assertions.assertTrue(query.stream().anyMatch(x -> x.getId().equals(m.getId())));

		semanticModelMapper.disableById(m.getId());
		semanticModelMapper.enableById(m.getId());

		m.setFieldDescription("desc2");
		int upd = semanticModelMapper.updateById(m);
		Assertions.assertEquals(1, upd);

		int del = semanticModelMapper.deleteById(m.getId());
		Assertions.assertEquals(1, del);
	}

	@Test
	public void testAgentKnowledgeCrud() {
		// insert
		AgentKnowledge k = new AgentKnowledge();
		k.setAgentId(1);
		k.setTitle("ak_title");
		k.setContent("ak_content");
		k.setType("document");
		k.setStatus("active");
		k.setEmbeddingStatus("pending");
		k.setCreateTime(LocalDateTime.now());
		k.setUpdateTime(LocalDateTime.now());
		int ins = agentKnowledgeMapper.insert(k);
		Assertions.assertEquals(1, ins);
		Assertions.assertNotNull(k.getId());

		List<AgentKnowledge> list = agentKnowledgeMapper.selectByAgentId(1);
		Assertions.assertTrue(list.stream().anyMatch(x -> x.getId().equals(k.getId())));

		k.setTitle("ak_title2");
		k.setUpdateTime(LocalDateTime.now());
		int upd = agentKnowledgeMapper.update(k);
		Assertions.assertEquals(1, upd);

		int del = agentKnowledgeMapper.deleteById(k.getId());
		Assertions.assertEquals(1, del);
	}

	@Test
	public void testAgentPresetQuestionCrud() {
		Long agentId = 9999L;
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
	}

	@Test
	public void testBusinessKnowledgeMapperCrud() {
		// clean
		businessKnowledgeMapper.selectByDatasetId("ds_ut").forEach(b -> businessKnowledgeMapper.deleteById(b.getId()));

		List<BusinessKnowledge> before = businessKnowledgeMapper.selectByDatasetId("ds_ut");
		Assertions.assertEquals(List.of(), before);

		BusinessKnowledge k = new BusinessKnowledge();
		k.setBusinessTerm("term_ut");
		k.setDescription("desc_ut");
		k.setSynonyms("a,b");
		k.setDefaultRecall(true);
		k.setDatasetId("ds_ut");
		k.setAgentId("1");
		int ins = businessKnowledgeMapper.insert(k);
		Assertions.assertEquals(1, ins);
		Assertions.assertNotNull(k.getId());

		List<BusinessKnowledge> byDataset = businessKnowledgeMapper.selectByDatasetId("ds_ut");
		Assertions.assertEquals(1, byDataset.size());

		List<BusinessKnowledge> search = businessKnowledgeMapper.searchByKeyword("term_ut");
		Assertions.assertTrue(search.stream().anyMatch(x -> x.getId().equals(k.getId())));

		k.setDescription("desc_ut_updated");
		int upd = businessKnowledgeMapper.updateById(k);
		Assertions.assertEquals(1, upd);

		int del = businessKnowledgeMapper.deleteById(k.getId());
		Assertions.assertEquals(1, del);
	}

}
