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
package com.alibaba.cloud.ai.dataagent.service.skill.impl;

import com.alibaba.cloud.ai.dataagent.dto.skill.SaveLocalSkillRequest;
import com.alibaba.cloud.ai.dataagent.properties.AgentSkillProperties;
import com.alibaba.cloud.ai.dataagent.service.skill.LocalSkillService;
import com.alibaba.cloud.ai.dataagent.vo.LocalSkillDetailVO;
import com.alibaba.cloud.ai.dataagent.vo.LocalSkillSummaryVO;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.SkillUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalSkillServiceImpl implements LocalSkillService {

	private static final String SKILL_FILE_NAME = "SKILL.md";

	private static final Pattern FRONT_MATTER_PATTERN = Pattern
		.compile("(?s)^---\\R(.*?)\\R---\\R?(.*)$");

	private static final Pattern FRONT_MATTER_LINE_PATTERN = Pattern.compile("(?m)^([A-Za-z0-9_\\-]+):\\s*(.+)$");

	private static final Pattern TITLE_PATTERN = Pattern.compile("(?m)^#\\s+(.+)$");

	private static final Pattern VALID_SKILL_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9\\-]{1,63}$");

	private static final Set<String> TEXT_RESOURCE_EXTENSIONS = Set.of(".md", ".txt", ".json", ".yaml", ".yml",
			".xml", ".csv", ".properties", ".java", ".js", ".ts", ".py", ".sql", ".sh", ".bat");

	private static final String BUILTIN_CURRENT_TIME_TITLE = "时间助手";

	private static final String BUILTIN_CURRENT_TIME_DESCRIPTION = "当用户需要当前时间、日期或格式化时间时启用这个 skill。";

	private static final String BUILTIN_CURRENT_TIME_CONTENT = """
			当用户询问当前时间、今天日期、某个时区现在几点，或者需要可靠的时间格式化结果时，使用这个 skill。

			工作原则：
			1. 优先调用 `skill.current_time.now` 工具获取精确时间，不要自行猜测。
			2. 回答时明确写出时区信息。
			3. 如果用户没有指定时区，默认使用 Asia/Shanghai。
			4. 如果用户要求特定格式，按要求输出；否则同时给出标准日期时间。
			""";

	private static final String RUNTIME_INSTRUCTION_HEADER = """
			以下 skills 已预加载为额外系统指令，不是隐式工具，也不是需要额外加载的插件。

			使用规则：
			1. 仅在用户请求与某个 skill 的描述或内容匹配时应用该 skill。
			2. 不要调用 `load_skill_through_path`。
			3. 不要臆造任何 `skill.<skill-id>.*`、`skill.<name>.*` 或其他未在当前可用工具列表中真实存在的工具。
			4. 只有工具列表里明确存在的工具才能调用。
			5. 如果 skill 内容提到了某个具体工具名，也只能在该工具真实可用时调用；否则把该 skill 当作普通提示词来遵循。
			""";

	private final AgentSkillProperties agentSkillProperties;

	@Override
	public List<LocalSkillSummaryVO> listSkills() {
		ensureStorageReady();
		try (Stream<Path> pathStream = Files.list(getStoragePath())) {
			return pathStream.filter(Files::isDirectory)
				.map(this::safeReadSkillDescriptor)
				.flatMap(Optional::stream)
				.sorted(Comparator.comparing(LocalSkillSummaryVO::builtin).reversed()
					.thenComparing(LocalSkillSummaryVO::updateTime, Comparator.reverseOrder()))
				.toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to list local skills: " + ex.getMessage(), ex);
		}
	}

	@Override
	public LocalSkillDetailVO getSkill(String skillId) {
		return readSkillDescriptor(skillId).toDetail();
	}

	@Override
	public LocalSkillDetailVO createSkill(SaveLocalSkillRequest request) {
		ensureStorageReady();
		String skillId = resolveSkillId(request.id(), request.title());
		if (isBuiltinSkill(skillId)) {
			throw new IllegalArgumentException("Built-in skill id is reserved: " + skillId);
		}
		Path skillDir = resolveSkillDirectory(skillId);
		if (Files.exists(skillDir)) {
			throw new IllegalArgumentException("Skill already exists: " + skillId);
		}
		writeSkill(skillId, request);
		return getSkill(skillId);
	}

	@Override
	public LocalSkillDetailVO updateSkill(String skillId, SaveLocalSkillRequest request) {
		if (isBuiltinSkill(skillId)) {
			throw new IllegalArgumentException("Built-in skill cannot be modified: " + skillId);
		}
		if (!exists(skillId)) {
			throw new IllegalArgumentException("Skill not found: " + skillId);
		}
		writeSkill(skillId, request);
		return getSkill(skillId);
	}

	@Override
	public void deleteSkill(String skillId) {
		if (isBuiltinSkill(skillId)) {
			throw new IllegalArgumentException("Built-in skill cannot be deleted: " + skillId);
		}
		Path skillDir = resolveSkillDirectory(skillId);
		if (!Files.exists(skillDir)) {
			return;
		}
		try (Stream<Path> pathStream = Files.walk(skillDir)) {
			pathStream.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ex) {
					throw new IllegalStateException("Failed to delete skill path: " + path, ex);
				}
			});
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to delete skill: " + skillId, ex);
		}
	}

	@Override
	public List<AgentSkill> loadAgentSkills(List<String> skillIds) {
		ensureStorageReady();
		if (skillIds == null || skillIds.isEmpty()) {
			return List.of();
		}
		List<AgentSkill> agentSkills = new ArrayList<>();
		for (String skillId : new LinkedHashSet<>(skillIds)) {
			if (!exists(skillId)) {
				log.warn("Skip missing local skill: {}", skillId);
				continue;
			}
			SkillDescriptor descriptor = readSkillDescriptor(skillId);
			try {
				agentSkills.add(SkillUtil.createFrom(descriptor.rawMarkdown(), loadResources(resolveSkillDirectory(skillId))));
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to load AgentScope skill: " + skillId, ex);
			}
		}
		return List.copyOf(agentSkills);
	}

	@Override
	public String buildRuntimeInstructions(List<String> skillIds) {
		ensureStorageReady();
		if (skillIds == null || skillIds.isEmpty()) {
			return "";
		}
		StringBuilder prompt = new StringBuilder(RUNTIME_INSTRUCTION_HEADER);
		for (String skillId : new LinkedHashSet<>(skillIds)) {
			if (!exists(skillId)) {
				log.warn("Skip missing local skill when building runtime instructions: {}", skillId);
				continue;
			}
			SkillDescriptor descriptor = readSkillDescriptor(skillId);
			prompt.append(System.lineSeparator())
				.append(System.lineSeparator())
				.append("## Skill: ")
				.append(descriptor.title())
				.append(System.lineSeparator())
				.append("skill_id: ")
				.append(descriptor.id())
				.append(System.lineSeparator())
				.append("description: ")
				.append(descriptor.description())
				.append(System.lineSeparator())
				.append(System.lineSeparator())
				.append(descriptor.content().trim());
			appendRuntimeResources(prompt, resolveSkillDirectory(skillId));
		}
		return prompt.toString().trim();
	}

	@Override
	public boolean exists(String skillId) {
		if (!StringUtils.hasText(skillId)) {
			return false;
		}
		return Files.exists(resolveSkillDirectory(skillId).resolve(SKILL_FILE_NAME));
	}

	@Override
	public boolean isBuiltinSkill(String skillId) {
		return BUILTIN_CURRENT_TIME_SKILL_ID.equals(skillId);
	}

	@Override
	public Path getStoragePath() {
		return agentSkillProperties.getLocalBasePath();
	}

	private void ensureStorageReady() {
		try {
			Files.createDirectories(getStoragePath());
			bootstrapBuiltinSkill();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to initialize local skill directory: " + getStoragePath(), ex);
		}
	}

	private void bootstrapBuiltinSkill() throws IOException {
		Path skillDir = resolveSkillDirectory(BUILTIN_CURRENT_TIME_SKILL_ID);
		Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
		if (Files.exists(skillFile)) {
			return;
		}
		Files.createDirectories(skillDir);
		Files.writeString(skillFile,
				renderSkillMarkdown(BUILTIN_CURRENT_TIME_SKILL_ID, BUILTIN_CURRENT_TIME_DESCRIPTION,
						BUILTIN_CURRENT_TIME_TITLE, BUILTIN_CURRENT_TIME_CONTENT),
				StandardCharsets.UTF_8);
	}

	private Optional<LocalSkillSummaryVO> safeReadSkillDescriptor(Path skillDir) {
		try {
			Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
			if (!Files.exists(skillFile)) {
				return Optional.empty();
			}
			return Optional.of(readSkillDescriptor(skillDir.getFileName().toString()).toSummary());
		}
		catch (Exception ex) {
			log.warn("Skip unreadable local skill directory: {}", skillDir, ex);
			return Optional.empty();
		}
	}

	private SkillDescriptor readSkillDescriptor(String skillId) {
		validateSkillId(skillId);
		Path skillDir = resolveSkillDirectory(skillId);
		Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
		if (!Files.exists(skillFile)) {
			throw new IllegalArgumentException("Skill not found: " + skillId);
		}
		try {
			String rawMarkdown = Files.readString(skillFile, StandardCharsets.UTF_8);
			SkillMarkdownParts markdownParts = parseSkillMarkdown(rawMarkdown);
			List<String> resources = listResources(skillDir);
			LocalDateTime updateTime = LocalDateTime.ofInstant(
					Instant.ofEpochMilli(Files.getLastModifiedTime(skillFile).toMillis()), ZoneId.systemDefault());
			return new SkillDescriptor(skillId, markdownParts.title(), markdownParts.description(),
					markdownParts.bodyWithoutTitle(), isBuiltinSkill(skillId), resources, updateTime, rawMarkdown);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read skill: " + skillId, ex);
		}
	}

	private void writeSkill(String skillId, SaveLocalSkillRequest request) {
		validateSkillId(skillId);
		String title = requireText(request.title(), "Skill title cannot be blank");
		String description = requireText(request.description(), "Skill description cannot be blank");
		String content = requireText(request.content(), "Skill content cannot be blank");
		Path skillDir = resolveSkillDirectory(skillId);
		Path skillFile = skillDir.resolve(SKILL_FILE_NAME);
		try {
			Files.createDirectories(skillDir);
			Files.writeString(skillFile, renderSkillMarkdown(skillId, description, title, content), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to save skill: " + skillId, ex);
		}
	}

	private Path resolveSkillDirectory(String skillId) {
		validateSkillId(skillId);
		Path skillDirectory = getStoragePath().resolve(skillId).normalize();
		if (!skillDirectory.startsWith(getStoragePath())) {
			throw new SecurityException("Invalid skill path");
		}
		return skillDirectory;
	}

	private String resolveSkillId(String requestedId, String title) {
		String base = StringUtils.hasText(requestedId) ? requestedId : title;
		String normalized = normalizeSkillId(base);
		if (!StringUtils.hasText(normalized)) {
			throw new IllegalArgumentException("Skill id cannot be resolved from input");
		}
		return normalized;
	}

	private String normalizeSkillId(String source) {
		if (!StringUtils.hasText(source)) {
			return "";
		}
		String slug = source.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
		slug = slug.replaceAll("^-+|-+$", "");
		if (slug.length() > 64) {
			slug = slug.substring(0, 64).replaceAll("-+$", "");
		}
		return slug;
	}

	private void validateSkillId(String skillId) {
		if (!StringUtils.hasText(skillId) || !VALID_SKILL_ID_PATTERN.matcher(skillId).matches()) {
			throw new IllegalArgumentException("Invalid skill id: " + skillId);
		}
	}

	private String requireText(String value, String message) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}

	private SkillMarkdownParts parseSkillMarkdown(String markdown) {
		Matcher matcher = FRONT_MATTER_PATTERN.matcher(markdown);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("SKILL.md must contain YAML front matter");
		}
		Map<String, String> frontMatter = new LinkedHashMap<>();
		Matcher lineMatcher = FRONT_MATTER_LINE_PATTERN.matcher(matcher.group(1));
		while (lineMatcher.find()) {
			frontMatter.put(lineMatcher.group(1).trim(), lineMatcher.group(2).trim());
		}
		String body = matcher.group(2) == null ? "" : matcher.group(2).trim();
		String description = frontMatter.getOrDefault("description", "");
		String title = extractTitle(body).orElse(frontMatter.getOrDefault("name", "未命名技能"));
		String bodyWithoutTitle = stripTitle(body);
		return new SkillMarkdownParts(title, description, bodyWithoutTitle);
	}

	private Optional<String> extractTitle(String markdownBody) {
		Matcher titleMatcher = TITLE_PATTERN.matcher(markdownBody);
		if (!titleMatcher.find()) {
			return Optional.empty();
		}
		return Optional.ofNullable(titleMatcher.group(1)).map(String::trim).filter(StringUtils::hasText);
	}

	private String stripTitle(String markdownBody) {
		Matcher titleMatcher = TITLE_PATTERN.matcher(markdownBody);
		if (!titleMatcher.find()) {
			return markdownBody.trim();
		}
		return markdownBody.substring(titleMatcher.end()).trim();
	}

	private String renderSkillMarkdown(String skillId, String description, String title, String content) {
		return """
				---
				name: %s
				description: %s
				---
				# %s

				%s
				""".formatted(skillId, escapeFrontMatterValue(description), title.trim(), content.trim());
	}

	private String escapeFrontMatterValue(String value) {
		return value.replace("\r", " ").replace("\n", " ").trim();
	}

	private List<String> listResources(Path skillDir) throws IOException {
		try (Stream<Path> pathStream = Files.walk(skillDir)) {
			return pathStream.filter(Files::isRegularFile)
				.filter(path -> !Objects.equals(path.getFileName().toString(), SKILL_FILE_NAME))
				.map(path -> skillDir.relativize(path).toString().replace('\\', '/'))
				.sorted()
				.toList();
		}
	}

	private Map<String, String> loadResources(Path skillDir) throws IOException {
		Map<String, String> resources = new LinkedHashMap<>();
		for (String resourcePath : listResources(skillDir)) {
			Path path = skillDir.resolve(resourcePath).normalize();
			if (!path.startsWith(skillDir) || !isTextResource(path)) {
				continue;
			}
			resources.put(resourcePath, Files.readString(path, StandardCharsets.UTF_8));
		}
		return resources;
	}

	private boolean isTextResource(Path path) {
		String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return TEXT_RESOURCE_EXTENSIONS.stream().anyMatch(filename::endsWith);
	}

	private void appendRuntimeResources(StringBuilder prompt, Path skillDir) {
		try {
			Map<String, String> resources = loadResources(skillDir);
			if (resources.isEmpty()) {
				return;
			}
			for (Map.Entry<String, String> entry : resources.entrySet()) {
				prompt.append(System.lineSeparator())
					.append(System.lineSeparator())
					.append("### Resource: ")
					.append(entry.getKey())
					.append(System.lineSeparator())
					.append(entry.getValue().trim());
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load skill resources for runtime prompt: " + skillDir, ex);
		}
	}

	private record SkillMarkdownParts(String title, String description, String bodyWithoutTitle) {
	}

	private record SkillDescriptor(String id, String title, String description, String content, boolean builtin,
			List<String> resources, LocalDateTime updateTime, String rawMarkdown) {

		private LocalSkillSummaryVO toSummary() {
			return new LocalSkillSummaryVO(id, title, description, builtin, resources.size(), updateTime);
		}

		private LocalSkillDetailVO toDetail() {
			return new LocalSkillDetailVO(id, title, description, builtin, content, List.copyOf(resources));
		}

	}

}
