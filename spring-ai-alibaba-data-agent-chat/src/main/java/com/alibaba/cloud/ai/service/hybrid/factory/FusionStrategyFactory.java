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
package com.alibaba.cloud.ai.service.hybrid.factory;

import com.alibaba.cloud.ai.constant.Constant;
import com.alibaba.cloud.ai.service.hybrid.fusion.FusionStrategy;
import com.alibaba.cloud.ai.service.hybrid.fusion.impl.RrfFusionStrategy;
import com.alibaba.cloud.ai.service.hybrid.fusion.impl.WeightedAverageStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FusionStrategy工厂类，根据配置创建相应的FusionStrategy实现类
 */
@Slf4j
@Configuration
public class FusionStrategyFactory {

	@Value("${" + Constant.PROJECT_PROPERTIES_PREFIX + ".fusion-strategy:rrf}")
	private String fusionStrategyType;

	/**
	 * 根据配置创建FusionStrategy实例
	 * @return FusionStrategy实例
	 */
	@Bean
	@ConditionalOnMissingBean(FusionStrategy.class)
	public FusionStrategy fusionStrategy() {
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

}
