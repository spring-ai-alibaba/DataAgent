package com.alibaba.cloud.ai.service.business.impls;

import com.alibaba.cloud.ai.dto.BusinessKnowledgeDTO;
import com.alibaba.cloud.ai.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.mapper.BusinessKnowledgeMapper;
import com.alibaba.cloud.ai.service.business.BusinessKnowledgeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BusinessKnowledgeServiceImpl implements BusinessKnowledgeService {

	private final BusinessKnowledgeMapper businessKnowledgeMapper;

	public BusinessKnowledgeServiceImpl(BusinessKnowledgeMapper businessKnowledgeMapper) {
		this.businessKnowledgeMapper = businessKnowledgeMapper;
	}

	@Override
	public List<BusinessKnowledge> getKnowledge(Long agentId) {
		return businessKnowledgeMapper.selectByAgentId(agentId);
	}

	@Override
	public List<BusinessKnowledge> getKnowledgeRecalled(Long agentId) {
		return businessKnowledgeMapper.selectRecalledByAgentId(agentId);
	}

	@Override
	public List<BusinessKnowledge> getAllKnowledge() {
		return businessKnowledgeMapper.selectAll();
	}

	@Override
	public List<BusinessKnowledge> searchKnowledge(Long agentId, String keyword) {
		return businessKnowledgeMapper.searchInAgent(agentId, keyword);
	}

	@Override
	public BusinessKnowledge getKnowledgeById(Long id) {
		return businessKnowledgeMapper.selectById(id);
	}

	@Override
	public Long addKnowledge(BusinessKnowledgeDTO knowledgeDTO) {
		BusinessKnowledge entity = knowledgeDTO.toEntity();
		if (businessKnowledgeMapper.insert(entity) > 0) {
			return entity.getId();
		}
		throw new RuntimeException("Failed to add knowledge");
	}

	@Override
	public void updateKnowledge(Long id, BusinessKnowledgeDTO knowledgeDTO) {
		BusinessKnowledge entity = knowledgeDTO.toEntity();
		entity.setId(id);
		if (businessKnowledgeMapper.updateById(entity) > 0) {
			return;
		}
		throw new RuntimeException("Failed to update knowledge");
	}

	@Override
	public void deleteKnowledge(Long id) {
		if (businessKnowledgeMapper.deleteById(id) > 0) {
			return;
		}
		throw new RuntimeException("Failed to delete knowledge");
	}

	@Override
	public void recallKnowledge(Long id, boolean isRecall) {
		if (businessKnowledgeMapper.changeRecall(id, isRecall) > 0) {
			return;
		}
		throw new RuntimeException("Failed to change recall status");
	}

}
