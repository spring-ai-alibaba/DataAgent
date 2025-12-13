/*
 * Copyright 2024-2025 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.service.hybrid.factory;

import com.alibaba.cloud.ai.dataagent.common.constant.Constant;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl.RrfFusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl.WeightedAverageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FusionStrategy工厂类，根据配置创建相应的FusionStrategy实现类
 */
@Slf4j
@Component
public class FusionStrategyFactory implements FactoryBean<FusionStrategy> {

	@Value("${" + Constant.PROJECT_PROPERTIES_PREFIX + ".fusion-strategy:rrf}")
	private String fusionStrategyType;

	/**
	 * 创建FusionStrategy实例
	 * @return FusionStrategy实例
	 */
	@Override
	public FusionStrategy getObject() throws Exception {
		log.info("Creating FusionStrategy with type: {}", fusionStrategyType);

		return switch (fusionStrategyType.toLowerCase()) {
			case "rrf" -> {
				log.info("Creating RrfFusionStrategy instance");
				yield new RrfFusionStrategy();
			}
			case "weighted" -> {
				log.info("Creating WeightedAverageStrategy instance");
				yield new WeightedAverageStrategy();
			}
			default -> {
				log.warn("Unknown fusion strategy type: {}, falling back to RrfFusionStrategy", fusionStrategyType);
				yield new RrfFusionStrategy();
			}
		};
	}

	/**
	 * 返回FusionStrategy的类型
	 * @return FusionStrategy类对象
	 */
	@Override
	public Class<?> getObjectType() {
		return FusionStrategy.class;
	}

	/**
	 * 设置为单例模式
	 * @return true
	 */
	@Override
	public boolean isSingleton() {
		return true;
	}

}
