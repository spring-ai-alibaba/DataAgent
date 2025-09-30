/**
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Agent Data Source Association Entity Class
 *
 * @author Alibaba Cloud AI
 */
@TableName("agent_datasource")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentDatasource {

	@TableId(value = "id", type = IdType.AUTO)
	private Integer id;

	@TableField("agent_id")
	private Integer agentId;

	@TableField("datasource_id")
	private Integer datasourceId;

	@TableField("is_active")
	private Integer isActive;

	@TableField(value = "create_time", fill = FieldFill.INSERT)
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createTime;

	@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime updateTime;

    // Associated data source object (for joint query)
    @TableField(exist = false)
    private Datasource datasource;

    public AgentDatasource(Integer agentId, Integer datasourceId) {
		this.agentId = agentId;
		this.datasourceId = datasourceId;
		this.isActive = 1;
	}


}
