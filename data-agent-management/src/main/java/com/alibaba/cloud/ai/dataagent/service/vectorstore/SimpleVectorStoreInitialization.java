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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

/**
 * @author David Yu
 */
@Slf4j
@RequiredArgsConstructor
public class SimpleVectorStoreInitialization implements ApplicationRunner, DisposableBean {

	private final SimpleVectorStore vectorStore;

	private final DataAgentProperties properties;

	public void load() {
		File file = new File(properties.getVectorStore().getFilePath());

		if (!file.exists()) {
			log.info("No locally serialized vector database file was found.");
			return;
		}

		try {
			vectorStore.load(file);
		}
		catch (Throwable throwable) {
			log.error("Failed to load the locally serialized vector database file.", throwable);
		}
	}

	public void save() {
		log.info("Serialize the vector database to a local file.");
		Path path = Paths.get(properties.getVectorStore().getFilePath());
		Path temporaryFile = null;

		try {
			Path parent = path.toAbsolutePath().getParent();
			Files.createDirectories(parent);
			temporaryFile = Files.createTempFile(parent, "vectorstore-", ".tmp");
			vectorStore.save(temporaryFile.toFile());
			try {
				Files.move(temporaryFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException unsupported) {
				Files.move(temporaryFile, path, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (Throwable t) {
			log.error("An exception occurred while serializing the vector database to a local file.", t);
		}
		finally {
			if (temporaryFile != null) {
				try {
					Files.deleteIfExists(temporaryFile);
				}
				catch (Exception cleanupError) {
					log.warn("Failed to remove temporary vector-store file: {}", temporaryFile, cleanupError);
				}
			}
		}
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		this.load();
	}

	@Override
	public void destroy() {
		this.save();
	}

}
