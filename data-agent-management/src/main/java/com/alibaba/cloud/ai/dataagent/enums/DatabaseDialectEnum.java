/*
 * Copyright 2024-2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.enums;

import java.util.Optional;

public enum DatabaseDialectEnum {

	MYSQL("MySQL"),

	SQLite("SQLite"),

	POSTGRESQL("PostgreSQL"),

	H2("H2"),

	DAMENG("Dameng"),

	SQL_SERVER("SqlServer"),

	ORACLE("Oracle");

	public final String code;

	DatabaseDialectEnum(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public static Optional<DatabaseDialectEnum> getByCode(String code) {
		for (DatabaseDialectEnum value : values()) {
			if (value.code.equals(code)) {
				return Optional.of(value);
			}
		}
		return Optional.empty();
	}

}
