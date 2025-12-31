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
package com.alibaba.cloud.ai.dataagent.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class InEnumValidator implements ConstraintValidator<InEnum, Object> {

	private final Set<Object> allowedValues = new HashSet<>();

	@Override
	public void initialize(InEnum annotation) {
		Class<? extends Enum<?>> enumClass = annotation.value();
		String methodName = annotation.method();

		Enum<?>[] enums = enumClass.getEnumConstants();

		for (Enum<?> enumVal : enums) {
			try {
				// 如果是默认的 "name"，直接获取 name()
				if ("name".equals(methodName)) {
					allowedValues.add(enumVal.name());
				}
				else {
					// 否则利用反射调用指定的方法（比如 getCode）获取值
					Method method = enumClass.getMethod(methodName);
					// 允许访问私有方法（视情况而定，一般public不需要）
					method.setAccessible(true);
					Object val = method.invoke(enumVal);
					allowedValues.add(val);
				}
			}
			catch (Exception e) {
				throw new RuntimeException("校验注解初始化失败，无法获取枚举方法: " + methodName, e);
			}
		}
	}

	@Override
	public boolean isValid(Object value, ConstraintValidatorContext context) {
		// 允许为空（空值检查通常由 @NotNull 处理）
		if (value == null) {
			return true;
		}

		if (value instanceof String && ((String) value).isEmpty()) {
			return true;
		}

		// 核心校验：看 Set 里有没有这个值
		return allowedValues.contains(value);
	}

}
