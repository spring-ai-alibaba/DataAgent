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

import { ref, onBeforeUnmount } from 'vue';

/**
 * Typewriter effect composable - simulates character-by-character streaming
 * similar to ChatGPT / DeepSeek rendering style.
 *
 * Strategy:
 *   - Maintain a queue of pending characters to display
 *   - Each animation frame, flush up to CHARS_PER_FRAME characters
 *   - This decouples the SSE arrival rate from the visual render rate,
 *     giving a smooth typewriter feel without blocking the main thread.
 */

// Characters to consume per animation frame.
// ~60fps × 3 chars = ~180 chars/s — comfortable reading pace like ChatGPT.
const CHARS_PER_FRAME = 3;

export function useTypewriter() {
	/** The text currently shown to the user */
	const displayedText = ref('');

	/** Internal queue: how many source characters have been "enqueued" */
	let enqueuedLength = 0;
	/** Full source text (always grows, never shrinks during a stream) */
	let sourceText = '';

	let rafId: number | null = null;
	let isActive = true;

	function tick() {
		rafId = null;
		if (!isActive) return;

		const current = displayedText.value.length;
		const target = enqueuedLength;

		if (current < target) {
			const end = Math.min(current + CHARS_PER_FRAME, target);
			displayedText.value = sourceText.slice(0, end);
		}

		// Keep ticking if there's still text to display
		if (displayedText.value.length < enqueuedLength) {
			rafId = requestAnimationFrame(tick);
		}
	}

	function scheduleTick() {
		if (!rafId && isActive) {
			rafId = requestAnimationFrame(tick);
		}
	}

	/**
	 * Append new text chunk to the typewriter queue.
	 * Call this every time a new SSE chunk arrives.
	 */
	function append(chunk: string) {
		sourceText += chunk;
		enqueuedLength = sourceText.length;
		scheduleTick();
	}

	/**
	 * Reset the typewriter to empty state (e.g. when starting a new stream).
	 */
	function reset() {
		if (rafId) {
			cancelAnimationFrame(rafId);
			rafId = null;
		}
		sourceText = '';
		enqueuedLength = 0;
		displayedText.value = '';
	}

	/**
	 * Flush all remaining queued text immediately (e.g. on stream complete).
	 */
	function flush() {
		if (rafId) {
			cancelAnimationFrame(rafId);
			rafId = null;
		}
		displayedText.value = sourceText;
		enqueuedLength = sourceText.length;
	}

	onBeforeUnmount(() => {
		isActive = false;
		if (rafId) cancelAnimationFrame(rafId);
	});

	return { displayedText, append, reset, flush };
}
