/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.dataagent.aop;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * AOP切面类，用于记录所有Node类的入口日志
 *
 *
 */
@Aspect
@Component
@Slf4j
public class NodeEntryLoggingAspect {

	@Pointcut("execution(* com.alibaba.cloud.ai.dataagent.workflow.node..*.apply(com.alibaba.cloud.ai.graph.OverAllState))")
	public void nodeEntry() {
	}

	/**
	 * 在所有实现NodeAction接口的类的apply方法执行前记录日志
	 * @param joinPoint 连接点
	 */
	@Before("nodeEntry()")
	public void logNodeEntry(JoinPoint joinPoint) {
		String className = joinPoint.getTarget().getClass().getSimpleName();
		log.info("Entering {} node", className);

		// 获取方法参数并打印状态信息
		Object[] args = joinPoint.getArgs();
		if (args != null && args.length > 0 && args[0] instanceof OverAllState state) {
			log.debug("State: {}", state);
		}
	}

}
