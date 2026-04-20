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

import { Project, SyntaxKind } from 'ts-morph';
import path from 'path';
import fs from 'fs/promises';

const project = new Project();

/**
 * 辅助函数：从 JSDoc 中提取描述
 * 优先获取 @description 标签，如果没有则获取主描述
 */
function getDocDescription(node) {
  if (!node || !node.getJsDocs) return '';
  const jsDocs = node.getJsDocs();
  if (jsDocs.length === 0) return '';
  
  const jsDoc = jsDocs[0];
  let description = jsDoc.getDescription().trim();
  
  // 寻找 @description 标签
  const descriptionTag = jsDoc.getTags().find(tag => tag.getTagName() === 'description');
  if (descriptionTag) {
    const tagText = descriptionTag.getText().replace(/^@description\s+/, '').trim();
    if (tagText) description = tagText;
  }
  
  // 清理描述中的多余星号和换行
  return description.replace(/^\s*\*\s?/gm, '').trim();
}

/**
 * @description 生成 TypeScript 逻辑模块的 AI 文档
 */
export async function generateLogicDocs(filePath) {
  const sourceFile = project.addSourceFileAtPath(filePath);
  const dir = path.dirname(filePath);
  const folderName = path.basename(dir);
  const fileName = path.basename(filePath);
  
  // 如果文件名是 index.ts，则使用文件夹名作为模块名
  const name = fileName === 'index.ts' ? folderName : fileName;

  let markdown = `# 逻辑模块: ${name}\n\n`;

  // 1. 提取文件级描述
  let fileDocs = '';
  const firstStatement = sourceFile.getStatements()[0];
  if (firstStatement) {
    fileDocs = getDocDescription(firstStatement);
  }
  
  if (!fileDocs) {
    const allJsDocs = sourceFile.getDescendantsOfKind(SyntaxKind.JSDocComment);
    if (allJsDocs.length > 0) {
      const jsDoc = allJsDocs[0];
      fileDocs = jsDoc.getDescription().trim().replace(/^\s*\*\s?/gm, '').trim();
      const descriptionTag = jsDoc.getTags().find(tag => tag.getTagName() === 'description');
      if (descriptionTag) {
        const tagText = descriptionTag.getText().replace(/^@description\s+/, '').trim();
        if (tagText) fileDocs = tagText.replace(/^\s*\*\s?/gm, '').trim();
      }
    }
  }

  if (fileDocs) {
    markdown += `## 模块描述\n${fileDocs}\n\n`;
  }

  // 2. 提取类
  const classes = sourceFile.getClasses();
  if (classes.length > 0) {
    markdown += `## 类 (Classes)\n`;
    classes.forEach(c => {
      const className = c.getName() || 'Anonymous Class';
      const classDocs = getDocDescription(c);
      markdown += `### Class: \`${className}\`\n${classDocs || '无描述'}\n`;
      
      const methods = c.getMethods().filter(m => m.getScope() === 'public' || !m.getScope());
      if (methods.length > 0) {
        markdown += `#### 公开方法:\n`;
        methods.forEach(m => {
          const methodDocs = getDocDescription(m);
          markdown += `- \`${m.getName()}\`: ${methodDocs || '无描述'}\n`;
        });
      }
      markdown += '\n';
    });
  }

  // 3. 提取函数
  const functions = sourceFile.getFunctions().filter(f => f.isExported() || classes.length === 0);
  if (functions.length > 0) {
    markdown += `## 函数 (Functions)\n`;
    functions.forEach(f => {
      const funcDocs = getDocDescription(f);
      markdown += `### \`${f.getName() || 'anonymous'}\`\n- **描述**: ${funcDocs || '无描述'}\n- **签名**: \`${f.getSignature().getDeclaration().getText()}\`\n\n`;
    });
  }

  // 4. 提取导出的变量 (如 Store, Composables)
  const variableStatements = sourceFile.getVariableStatements().filter(v => v.isExported());
  if (variableStatements.length > 0) {
    markdown += `## 导出变量 (Variables/Stores)\n`;
    variableStatements.forEach(v => {
      const docs = getDocDescription(v);
      v.getDeclarations().forEach(decl => {
        markdown += `### \`${decl.getName()}\`\n- **描述**: ${docs || '无描述'}\n`;
        markdown += '\n';
      });
    });
  }

  // 5. 提取接口
  const interfaces = sourceFile.getInterfaces().filter(i => i.isExported());
  if (interfaces.length > 0) {
    markdown += `## 类型定义 (Interfaces)\n`;
    interfaces.forEach(i => {
      const interfaceDocs = getDocDescription(i);
      markdown += `### \`${i.getName()}\`\n${interfaceDocs ? `**描述**: ${interfaceDocs}\n` : ''}\`\`\`typescript\n${i.getText()}\n\`\`\`\n\n`;
    });
  }

  markdown += `\n---\n> 🤖 AI 提示: 逻辑实现请参考 \`${path.join(folderName, fileName)}\`。`;

  await fs.writeFile(path.join(dir, 'README.md'), markdown);
  console.log(`  - [Logic] 已更新: ${name}`);
  
  // 释放内存
  project.removeSourceFile(sourceFile);
}
