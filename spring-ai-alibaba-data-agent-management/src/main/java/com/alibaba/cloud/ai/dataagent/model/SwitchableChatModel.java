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
package com.alibaba.cloud.ai.dataagent.model;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 支持运行时热替换的 ChatModel 代理类。 系统中始终注入此 Bean，当配置变更时，调用 updateDelegate() 切换底层实现。
 */
@Slf4j
public class SwitchableChatModel implements ChatModel {

	// 使用 AtomicReference 保证多线程环境下的可见性和原子性
	private final AtomicReference<ChatModel> delegate = new AtomicReference<>();

	/**
	 * 构造函数
	 * @param initialModel 初始模型实例 (不能为空)
	 */
	public SwitchableChatModel(ChatModel initialModel) {
		Assert.notNull(initialModel, "Initial ChatModel must not be null");
		this.delegate.set(initialModel);
	}

	/**
	 * 核心方法：切换底层的模型实现
	 * @param newModel 新创建的模型实例
	 */
	public void updateDelegate(ChatModel newModel) {
		Assert.notNull(newModel, "New ChatModel must not be null");
		// 这一步是原子操作，切换瞬间旧请求用旧的，新请求用新的，不会报错
		this.delegate.set(newModel);
		log.info("ChatModel implementation has been switched to: {}", newModel.getClass().getSimpleName());
	}

	// --- 以下为 ChatModel 接口方法的委托实现 ---
	@Override
	public ChatResponse call(Prompt prompt) {
		// 获取当前引用的模型并调用
		return delegate.get().call(prompt);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		// 流式调用委托
		return delegate.get().stream(prompt);
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return delegate.get().getDefaultOptions();
	}

	// 注意：ChatModel 接口中还有很多 call(String msg) 等默认方法(default methods)，
	// 它们最终都会调用上面的 call(Prompt prompt)，所以不需要手动覆盖，
	// 它们会自动路由到我们覆盖了的 call(Prompt prompt) 方法上。

}
