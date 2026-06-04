/*
 * Copyright 2026 the original author or authors.
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

import { parse } from 'vue-docgen-api';
import path from 'path';
import fs from 'fs/promises';

/**
 * @description 手动提取 Vue 文件中的 @description
 */
async function extractManualDescription(filePath) {
	try {
		const content = await fs.readFile(filePath, 'utf-8');
		const match = content.match(/\/\*\*[\s\S]*?@description\s+([\s\S]*?)\*\//);
		if (match && match[1]) {
			return match[1].replace(/^\s*\*\s?/gm, '').trim();
		}
	} catch (e) {
		console.log(e);
	}
	return '';
}

/**
 * @description 生成 Vue 组件的 AI 文档
 */
export async function generateComponentDocs(filePath) {
	try {
		const componentInfo = await parse(filePath);
		const dir = path.dirname(filePath);
		const folderName = path.basename(dir);
		const fileName = path.basename(filePath);

		// 如果文件名是 index.vue，则使用文件夹名作为组件名
		const name =
			fileName === 'index.vue'
				? folderName
				: componentInfo.displayName || fileName;

		let markdown = `# 组件: ${name}\n\n`;

		// 提取描述
		let description = componentInfo.description || '';
		if (!description) {
			description = await extractManualDescription(filePath);
		}

		if (description) {
			markdown += `## 模块描述\n${description.trim()}\n\n`;
		}

		// Props
		if (componentInfo.props && componentInfo.props.length > 0) {
			markdown += `## Props\n| 属性 | 类型 | 默认值 | 描述 |\n| --- | --- | --- | --- |\n`;
			componentInfo.props.forEach((p) => {
				markdown += `| ${p.name} | \`${p.type?.name || 'any'}\` | ${p.defaultValue?.value || '-'} | ${p.description || '-'} |\n`;
			});
			markdown += '\n';
		}

		// Slots
		if (componentInfo.slots && componentInfo.slots.length > 0) {
			markdown += `## Slots\n| 名称 | 描述 |\n| --- | --- |\n`;
			componentInfo.slots.forEach((s) => {
				markdown += `| ${s.name} | ${s.description || '-'} |\n`;
			});
			markdown += '\n';
		}

		// Events
		if (componentInfo.events && componentInfo.events.length > 0) {
			markdown += `## Events\n| 名称 | 描述 |\n| --- | --- |\n`;
			componentInfo.events.forEach((e) => {
				markdown += `| ${e.name} | ${e.description || '-'} |\n`;
			});
			markdown += '\n';
		}

		markdown += `\n---\n> 🤖 AI 提示: 修改此组件前请阅读上述定义。代码位于 \`${path.join(folderName, fileName)}\`。`;

		await fs.writeFile(path.join(dir, 'README.md'), markdown);
		console.log(`  - [UI] 已更新: ${name}`);
	} catch (e) {
		console.error(`  - [UI] 解析失败 ${filePath}:`, e.message);
	}
}
