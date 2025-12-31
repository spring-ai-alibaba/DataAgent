/*
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

package com.alibaba.cloud.ai.dataagent.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Datasource {

	private Integer id;

	private String name;

	private String type;

	private String host;

	private Integer port;

	private String databaseName;

	private String username;

	private String password;

	private String connectionUrl;

	private String status;

	private String testStatus;

	private String description;

	private Long creatorId;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createTime;

	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime updateTime;

	@Override
	public String toString() {
		return "Datasource{" + "id=" + id + ", name='" + name + '\'' + ", type='" + type + '\'' + ", host='" + host
				+ '\'' + ", port=" + port + ", databaseName='" + databaseName + '\'' + ", status='" + status + '\''
				+ ", testStatus='" + testStatus + '\'' + ", createTime=" + createTime + ", updateTime=" + updateTime
				+ '}';
	}

}
