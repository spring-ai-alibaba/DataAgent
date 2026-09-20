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
package com.alibaba.cloud.ai.dataagent.service.agent;

import com.alibaba.cloud.ai.dataagent.entity.AgentPresetQuestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@MybatisTest
@Import(AgentPresetQuestionServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentPresetQuestionServiceTransactionIntegrationTest {

	@Autowired
	private AgentPresetQuestionService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("DROP TABLE IF EXISTS agent_preset_question");
		String createTableSql = "CREATE TABLE IF NOT EXISTS agent_preset_question ("
				+ "  id INT NOT NULL AUTO_INCREMENT," + "  agent_id INT NOT NULL COMMENT '智能体ID',"
				+ "  question TEXT NOT NULL COMMENT '预设问题内容'," + "  sort_order INT DEFAULT 0 COMMENT '排序顺序',"
				+ "  is_active TINYINT DEFAULT 0 COMMENT '是否启用：0-禁用，1-启用',"
				+ "  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',"
				+ "  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',"
				+ "  PRIMARY KEY (id)" + ")";
		jdbcTemplate.execute(createTableSql);

		String sql = "INSERT INTO agent_preset_question (agent_id, question) VALUES (?, ?)";
		jdbcTemplate.update(sql, 1, "旧问题");
	}

	@Test
	void testBatchSave_deleteCanRollbackWhenInsertFails() {
		AgentPresetQuestion q1 = new AgentPresetQuestion();
		q1.setQuestion("q1");
		AgentPresetQuestion q2 = new AgentPresetQuestion();
		q2.setQuestion(null);

		assertThrowsExactly(DataIntegrityViolationException.class, () -> service.batchSave(1L, List.of(q1, q2)));
		String query = "SELECT question FROM agent_preset_question WHERE agent_id = ?";
		String question = jdbcTemplate.queryForObject(query, String.class, 1L);
		assertEquals("旧问题", question);
	}

}
