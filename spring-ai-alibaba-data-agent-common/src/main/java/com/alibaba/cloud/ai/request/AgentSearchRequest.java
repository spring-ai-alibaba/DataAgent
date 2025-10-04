package com.alibaba.cloud.ai.request;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class AgentSearchRequest implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String agentId;

	private String query;

	private Integer topK;

	private Map<String, Object> metadataFilter;

	/**
	 * 私有构造函数，禁止直接实例化 此构造函数仅内部使用，外部代码必须通过getInstance()方法创建实例
	 */
	private AgentSearchRequest(String agentId) {
		Objects.requireNonNull(agentId, "Agent ID cannot be null");
		this.agentId = agentId;
		// 初始化metadataFilter，确保始终包含agentId
		this.metadataFilter = Map.of("agentId", agentId);
	}

	/**
	 * 创建AgentSearchRequest实例的工厂方法
	 * @param agentId 代理ID，不能为空
	 * @return AgentSearchRequest实例
	 * @throws IllegalArgumentException 如果agentId为空
	 */
	public static AgentSearchRequest getInstance(String agentId) {
		return new AgentSearchRequest(agentId);
	}

	/**
	 * 创建AgentSearchRequest实例的工厂方法
	 * @param agentId 代理ID，不能为空
	 * @param query 查询内容
	 * @return AgentSearchRequest实例
	 * @throws IllegalArgumentException 如果agentId为空
	 */
	public static AgentSearchRequest getInstance(String agentId, String query) {
		AgentSearchRequest request = new AgentSearchRequest(agentId);
		request.setQuery(query);
		return request;
	}

	/**
	 * 创建AgentSearchRequest实例的工厂方法
	 * @param agentId 代理ID，不能为空
	 * @param query 查询内容
	 * @param topK 返回结果数量
	 * @return AgentSearchRequest实例
	 * @throws IllegalArgumentException 如果agentId为空
	 */
	public static AgentSearchRequest getInstance(String agentId, String query, Integer topK) {
		AgentSearchRequest request = new AgentSearchRequest(agentId);
		request.setQuery(query);
		request.setTopK(topK);
		return request;
	}

	public String getAgentId() {
		return agentId;
	}

	public Map<String, Object> getMetadataFilter() {
		return metadataFilter;
	}

	public void setMetadataFilter(Map<String, Object> metadataFilter) {
		if (metadataFilter == null) {
			// 如果传入null，则创建只包含agentId的map
			this.metadataFilter = Map.of("agentId", agentId);
		}
		else {
			// 创建新的map，包含传入的所有参数和agentId
			Map<String, Object> newFilter = new java.util.HashMap<>(metadataFilter);
			newFilter.put("agentId", agentId);
			this.metadataFilter = Map.copyOf(newFilter); // 创建不可变副本
		}
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public Integer getTopK() {
		return topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public String toString() {
		return "AgentSearchRequest{" + "agentId='" + agentId + '\'' + ", query='" + query + '\'' + ", topK=" + topK
				+ ", metadataFilter=" + metadataFilter + '}';
	}

}
