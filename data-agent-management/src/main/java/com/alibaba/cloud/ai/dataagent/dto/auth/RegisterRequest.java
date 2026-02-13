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
package com.alibaba.cloud.ai.dataagent.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

	@NotBlank(message = "用户名不能为空")
	@Size(min = 3, max = 50, message = "用户名长度需在3-50之间")
	private String username;

	@NotBlank(message = "密码不能为空")
	@Size(min = 6, max = 100, message = "密码长度需在6-100之间")
	private String password;

	@NotBlank(message = "邮箱不能为空")
	@Email(message = "邮箱格式不正确")
	private String email;

	private String realName;

}
