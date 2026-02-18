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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * @author zihenzzz
 * @date 2026/2/16 13:55
 */

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX + ".langfuse")
public class OpenTelemetryConfig {

	private static final String SERVICE_NAME = "data-agent";

	private boolean enabled = true;

	private String host;

	private String publicKey;

	private String secretKey;

	private SdkTracerProvider tracerProvider;

	@Bean
	public OpenTelemetry openTelemetry() {
		if (!enabled) {
			return OpenTelemetry.noop();
		}

		String auth = publicKey + ":" + secretKey;
		String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

		OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
			.setEndpoint(host + "/api/public/otel/v1/traces")
			.addHeader("Authorization", "Basic " + encodedAuth)
			.setTimeout(10, TimeUnit.SECONDS)
			.build();

		Resource resource = Resource.getDefault()
			.merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), SERVICE_NAME)));

		tracerProvider = SdkTracerProvider.builder()
			.addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
				.setScheduleDelay(1, TimeUnit.SECONDS)
				.setMaxExportBatchSize(100)
				.build())
			.setResource(resource)
			.build();

		OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

		log.info("OpenTelemetry initialized with Langfuse OTLP HTTP exporter");

		return openTelemetrySdk;
	}

	@Bean
	public Tracer langfuseTracer(OpenTelemetry openTelemetry) {
		return openTelemetry.getTracer(SERVICE_NAME);
	}

	@PreDestroy
	public void shutdown() {
		if (tracerProvider != null) {
			tracerProvider.close();
		}
	}

}
