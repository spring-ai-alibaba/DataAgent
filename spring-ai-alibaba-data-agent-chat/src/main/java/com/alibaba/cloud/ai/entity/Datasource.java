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
 * Data Source Entity Class
 *
 * @author Alibaba Cloud AI
 */
@TableName("datasource")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Datasource {

	@TableId(value = "id", type = IdType.AUTO)
	private Integer id;

	@TableField("name")
	private String name;

	@TableField("type")
	private String type;

	@TableField("host")
	private String host;

	@TableField("port")
	private Integer port;

	@TableField("database_name")
	private String databaseName;

	@TableField("username")
	private String username;

	@TableField("password")
	private String password;

	@TableField("connection_url")
	private String connectionUrl;

	@TableField("status")
	private String status;

	@TableField("test_status")
	private String testStatus;

	@TableField("description")
	private String description;

	@TableField("creator_id")
	private Long creatorId;

	@TableField(value = "create_time", fill = FieldFill.INSERT)
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createTime;

	@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime updateTime;

	public Datasource(String name, String type, String host, Integer port, String databaseName, String username,
			String password) {
		this.name = name;
		this.type = type;
		this.host = host;
		this.port = port;
		this.databaseName = databaseName;
		this.username = username;
		this.password = password;
		this.status = "active";
		this.testStatus = "unknown";
	}

	/**
	 * Generate connection URL
	 */
	public void generateConnectionUrl() {
		if (host != null && port != null && databaseName != null) {
			if ("mysql".equalsIgnoreCase(type)) {
				this.connectionUrl = String.format(
						"jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&transformedBitIsBoolean=true&allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai",
						host, port, databaseName);
			}
			else if ("postgresql".equalsIgnoreCase(type)) {
				this.connectionUrl = String.format(
						"jdbc:postgresql://%s:%d/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai",
						host, port, databaseName);
			}
			else if ("h2".equalsIgnoreCase(type)) {
				this.connectionUrl = String.format(
						"jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=true;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
						databaseName);
			}
		}
	}

	@Override
	public String toString() {
		return "Datasource{" + "id=" + id + ", name='" + name + '\'' + ", type='" + type + '\'' + ", host='" + host
				+ '\'' + ", port=" + port + ", databaseName='" + databaseName + '\'' + ", status='" + status + '\''
				+ ", testStatus='" + testStatus + '\'' + ", createTime=" + createTime + ", updateTime=" + updateTime
				+ '}';
	}

}
