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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentConverterUtilTest {

	@Test
	void testConvertColumnToDocument_withDescription() {
		TableInfoBO table = new TableInfoBO();
		table.setName("users");
		ColumnInfoBO col = new ColumnInfoBO();
		col.setName("user_id");
		col.setDescription("User identifier");
		col.setType("bigint");
		col.setPrimary(true);
		col.setNotnull(true);

		Document doc = DocumentConverterUtil.convertColumnToDocument(1, table, col);

		assertEquals("User identifier", doc.getText());
		assertEquals("user_id", doc.getMetadata().get("name"));
		assertEquals("users", doc.getMetadata().get("tableName"));
		assertEquals(DocumentMetadataConstant.COLUMN, doc.getMetadata().get(DocumentMetadataConstant.VECTOR_TYPE));
	}

	@Test
	void testConvertColumnToDocument_noDescription() {
		TableInfoBO table = new TableInfoBO();
		table.setName("users");
		ColumnInfoBO col = new ColumnInfoBO();
		col.setName("user_id");
		col.setType("int");

		Document doc = DocumentConverterUtil.convertColumnToDocument(1, table, col);
		assertEquals("user_id", doc.getText());
	}

	@Test
	void testConvertColumnToDocument_withSamples() {
		TableInfoBO table = new TableInfoBO();
		table.setName("t");
		ColumnInfoBO col = new ColumnInfoBO();
		col.setName("status");
		col.setType("varchar");
		col.setSamples("active,inactive");

		Document doc = DocumentConverterUtil.convertColumnToDocument(1, table, col);
		assertEquals("active,inactive", doc.getMetadata().get("samples"));
	}

	@Test
	void testConvertTableToDocument() {
		TableInfoBO table = new TableInfoBO();
		table.setName("orders");
		table.setDescription("Order table");
		table.setSchema("public");

		Document doc = DocumentConverterUtil.convertTableToDocument(1, table);

		assertEquals("Order table", doc.getText());
		assertEquals("orders", doc.getMetadata().get("name"));
		assertEquals(DocumentMetadataConstant.TABLE, doc.getMetadata().get(DocumentMetadataConstant.VECTOR_TYPE));
	}

	@Test
	void testConvertTableToDocument_noDescription() {
		TableInfoBO table = new TableInfoBO();
		table.setName("orders");

		Document doc = DocumentConverterUtil.convertTableToDocument(1, table);
		assertEquals("orders", doc.getText());
	}

	@Test
	void testConvertTablesToDocuments() {
		TableInfoBO t1 = new TableInfoBO();
		t1.setName("t1");
		TableInfoBO t2 = new TableInfoBO();
		t2.setName("t2");

		List<Document> docs = DocumentConverterUtil.convertTablesToDocuments(1, List.of(t1, t2));
		assertEquals(2, docs.size());
	}

	@Test
	void testConvertColumnsToDocuments() {
		TableInfoBO table = new TableInfoBO();
		table.setName("t");
		ColumnInfoBO c1 = new ColumnInfoBO();
		c1.setName("c1");
		c1.setType("int");
		ColumnInfoBO c2 = new ColumnInfoBO();
		c2.setName("c2");
		c2.setType("varchar");
		table.setColumns(List.of(c1, c2));

		List<Document> docs = DocumentConverterUtil.convertColumnsToDocuments(1, List.of(table));
		assertEquals(2, docs.size());
	}

	@Test
	void testConvertColumnsToDocuments_nullColumns() {
		TableInfoBO table = new TableInfoBO();
		table.setName("t");
		table.setColumns(null);

		List<Document> docs = DocumentConverterUtil.convertColumnsToDocuments(1, List.of(table));
		assertTrue(docs.isEmpty());
	}

	@Test
	void testConvertBusinessKnowledgeToDocument() {
		BusinessKnowledge bk = new BusinessKnowledge();
		bk.setId(1L);
		bk.setAgentId(10L);
		bk.setBusinessTerm("GMV");
		bk.setDescription("Gross Merchandise Value");
		bk.setSynonyms("总交易额");

		Document doc = DocumentConverterUtil.convertBusinessKnowledgeToDocument(bk);

		assertTrue(doc.getText().contains("GMV"));
		assertTrue(doc.getText().contains("Gross Merchandise Value"));
		assertEquals(DocumentMetadataConstant.BUSINESS_TERM,
				doc.getMetadata().get(DocumentMetadataConstant.VECTOR_TYPE));
	}

	@Test
	void testConvertQaFaqKnowledgeToDocument() {
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		knowledge.setAgentId(10);
		knowledge.setQuestion("How to reset password?");
		knowledge.setType(KnowledgeType.QA);

		Document doc = DocumentConverterUtil.convertQaFaqKnowledgeToDocument(knowledge);

		assertEquals("How to reset password?", doc.getText());
		assertEquals("10", doc.getMetadata().get("agentId"));
	}

	@Test
	void testConvertAgentKnowledgeDocumentsWithMetadata() {
		Document original = new Document("original content");
		AgentKnowledge knowledge = new AgentKnowledge();
		knowledge.setId(1);
		knowledge.setAgentId(10);
		knowledge.setType(KnowledgeType.DOCUMENT);

		List<Document> result = DocumentConverterUtil.convertAgentKnowledgeDocumentsWithMetadata(List.of(original),
				knowledge);

		assertEquals(1, result.size());
		assertEquals("original content", result.get(0).getText());
		assertEquals("10", result.get(0).getMetadata().get("agentId"));
		assertEquals(DocumentMetadataConstant.AGENT_KNOWLEDGE,
				result.get(0).getMetadata().get(DocumentMetadataConstant.VECTOR_TYPE));
	}

}
