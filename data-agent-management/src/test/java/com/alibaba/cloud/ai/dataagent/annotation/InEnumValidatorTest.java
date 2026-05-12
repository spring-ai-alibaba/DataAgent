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
package com.alibaba.cloud.ai.dataagent.annotation;

import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InEnumValidatorTest {

	@Mock
	private ConstraintValidatorContext context;

	@Test
	void testIsValid_null_returnsTrue() {
		InEnumValidator validator = createValidator(ModelType.class, "name");
		assertTrue(validator.isValid(null, context));
	}

	@Test
	void testIsValid_emptyString_returnsTrue() {
		InEnumValidator validator = createValidator(ModelType.class, "name");
		assertTrue(validator.isValid("", context));
	}

	@Test
	void testIsValid_validName() {
		InEnumValidator validator = createValidator(ModelType.class, "name");
		assertTrue(validator.isValid("CHAT", context));
		assertTrue(validator.isValid("EMBEDDING", context));
	}

	@Test
	void testIsValid_invalidName() {
		InEnumValidator validator = createValidator(ModelType.class, "name");
		assertFalse(validator.isValid("UNKNOWN", context));
	}

	@Test
	void testIsValid_customMethod() {
		InEnumValidator validator = createValidator(KnowledgeType.class, "getCode");
		assertTrue(validator.isValid("DOCUMENT", context));
		assertTrue(validator.isValid("QA", context));
		assertTrue(validator.isValid("FAQ", context));
		assertFalse(validator.isValid("UNKNOWN", context));
	}

	@Test
	void testInitialize_invalidMethodThrows() {
		assertThrows(RuntimeException.class, () -> createValidator(ModelType.class, "nonExistentMethod"));
	}

	@SuppressWarnings("unchecked")
	private InEnumValidator createValidator(Class<? extends Enum<?>> enumClass, String method) {
		InEnum annotation = mock(InEnum.class);
		doReturn(enumClass).when(annotation).value();
		when(annotation.method()).thenReturn(method);

		InEnumValidator validator = new InEnumValidator();
		validator.initialize(annotation);
		return validator;
	}

}
