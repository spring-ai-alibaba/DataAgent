#!/usr/bin/env node
/**
 * 一键为项目文件添加 Apache 2.0 版权头
 * 跳过 .json 和 .md 文件
 * 用法: node add-license-headers.mjs [目标目录]
 * 默认处理当前目录下所有子项目（排除 node_modules / .git / dist 等）
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const TARGET_DIR = process.argv[2]
  ? path.resolve(process.argv[2])
  : __dirname;

// ── 注释风格 ──────────────────────────────────────────────────────────────────

const BLOCK_EXTS  = new Set(['.vue', '.ts', '.js', '.mjs', '.cjs', '.css', '.scss', '.less', '.java', '.kt']);
const HTML_EXTS   = new Set(['.html']);
const HASH_EXTS   = new Set(['.py', '.sh', '.yaml', '.yml']);

// ── 跳过规则 ──────────────────────────────────────────────────────────────────

const SKIP_EXTS = new Set([
  '.json', '.md', '.lock', '.svg', '.png', '.jpg', '.jpeg',
  '.ico', '.gif', '.webp', '.woff', '.woff2', '.ttf', '.eot',
  '.map', '.d.ts', '.zip', '.tar', '.gz',
]);

const SKIP_DIRS = new Set([
  'node_modules', '.nuxt', '.output', 'dist', 'build', 'target',
  '.git', '.scripts', '.idea', '.vscode', '.gradle', 'coverage',
  '__pycache__', '.pnpm-store',
]);

// ── 版权正文 ──────────────────────────────────────────────────────────────────

const LICENSE_LINES = [
  'Copyright 2026 the original author or authors.',
  '',
  'Licensed under the Apache License, Version 2.0 (the "License");',
  'you may not use this file except in compliance with the License.',
  'You may obtain a copy of the License at',
  '',
  '     https://www.apache.org/licenses/LICENSE-2.0',
  '',
  'Unless required by applicable law or agreed to in writing, software',
  'distributed under the License is distributed on an "AS IS" BASIS,',
  'WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.',
  'See the License for the specific language governing permissions and',
  'limitations under the License.',
];

// ── 构建注释头 ────────────────────────────────────────────────────────────────

function buildBlockHeader() {
  const lines = LICENSE_LINES.map(l => (l ? ` * ${l}` : ' *'));
  return `/*\n${lines.join('\n')}\n */\n\n`;
}

function buildHtmlHeader() {
  const lines = LICENSE_LINES.map(l => (l ? ` * ${l}` : ' *'));
  return `<!--\n${lines.join('\n')}\n-->\n\n`;
}

function buildHashHeader() {
  const lines = LICENSE_LINES.map(l => (l ? `# ${l}` : '#'));
  return `${lines.join('\n')}\n\n`;
}

function getHeader(ext) {
  if (BLOCK_EXTS.has(ext)) return buildBlockHeader();
  if (HTML_EXTS.has(ext))  return buildHtmlHeader();
  if (HASH_EXTS.has(ext))  return buildHashHeader();
  return null;
}

// ── 检测是否已有版权头 ────────────────────────────────────────────────────────

function alreadyHasHeader(content) {
  const head = content.slice(0, 500);
  return (
    head.includes('Copyright') ||
    head.includes('Licensed under') ||
    head.includes('Apache License')
  );
}

// ── 遍历处理 ──────────────────────────────────────────────────────────────────

let added = 0;
let skipped = 0;
let errors = 0;

function processDir(dir) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);

    if (entry.isDirectory()) {
      if (!SKIP_DIRS.has(entry.name)) processDir(fullPath);
      continue;
    }

    if (!entry.isFile()) continue;

    const ext = path.extname(entry.name).toLowerCase();
    if (SKIP_EXTS.has(ext)) { skipped++; continue; }

    // .d.ts 文件跳过
    if (entry.name.endsWith('.d.ts')) { skipped++; continue; }

    const header = getHeader(ext);
    if (!header) { skipped++; continue; }

    try {
      const content = fs.readFileSync(fullPath, 'utf-8');
      if (alreadyHasHeader(content)) { skipped++; continue; }

      fs.writeFileSync(fullPath, header + content, 'utf-8');
      console.log('✅ ', path.relative(__dirname, fullPath));
      added++;
    } catch (e) {
      console.error('❌  Error:', fullPath, e.message);
      errors++;
    }
  }
}

console.log(`\n🚀 处理目录: ${path.relative(__dirname, TARGET_DIR) || '.'}\n`);
processDir(TARGET_DIR);
console.log(`\n──────────────────────────────────────────`);
console.log(`✅  新增版权头: ${added} 个文件`);
console.log(`⏩  已有/跳过: ${skipped} 个文件`);
if (errors > 0) console.log(`❌  处理失败: ${errors} 个文件`);
console.log(`──────────────────────────────────────────\n`);
