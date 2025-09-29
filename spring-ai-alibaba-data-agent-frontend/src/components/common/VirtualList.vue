<!--
 * Copyright 2025 the original author or authors.
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
-->
<template>
  <div class="virtual-list" :style="containerStyle" @scroll="handleScroll">
    <div class="virtual-list-phantom" :style="phantomStyle"></div>
    <div class="virtual-list-content" :style="contentStyle">
      <div
        v-for="item in visibleItems"
        :key="getItemKey(item, item.index)"
        :style="getItemStyle(item.index)"
        class="virtual-list-item"
      >
        <slot :item="item.data" :index="item.index">
          {{ item.data }}
        </slot>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'

export default {
  name: 'VirtualList',
  props: {
    items: {
      type: Array,
      required: true
    },
    itemHeight: {
      type: Number,
      default: 50
    },
    containerHeight: {
      type: Number,
      default: 400
    },
    buffer: {
      type: Number,
      default: 5
    },
    itemKey: {
      type: [String, Function],
      default: 'id'
    }
  },
  emits: ['scroll'],
  setup(props, { emit }) {
    const scrollTop = ref(0)
    const containerRef = ref(null)

    // 计算可见区域
    const visibleRange = computed(() => {
      const start = Math.floor(scrollTop.value / props.itemHeight)
      const end = Math.min(
        start + Math.ceil(props.containerHeight / props.itemHeight) + props.buffer,
        props.items.length
      )
      return {
        start: Math.max(0, start - props.buffer),
        end
      }
    })

    // 可见项目
    const visibleItems = computed(() => {
      const { start, end } = visibleRange.value
      return props.items.slice(start, end).map((item, index) => ({
        data: item,
        index: start + index
      }))
    })

    // 容器样式
    const containerStyle = computed(() => ({
      height: `${props.containerHeight}px`,
      overflow: 'auto',
      position: 'relative'
    }))

    // 占位元素样式
    const phantomStyle = computed(() => ({
      height: `${props.items.length * props.itemHeight}px`,
      position: 'absolute',
      top: 0,
      left: 0,
      right: 0,
      zIndex: -1
    }))

    // 内容区域样式
    const contentStyle = computed(() => ({
      position: 'absolute',
      top: 0,
      left: 0,
      right: 0,
      top: `${visibleRange.value.start * props.itemHeight}px`
    }))

    // 获取项目样式
    const getItemStyle = (index) => ({
      height: `${props.itemHeight}px`,
      lineHeight: `${props.itemHeight}px`
    })

    // 获取项目键值
    const getItemKey = (item, index) => {
      if (typeof props.itemKey === 'function') {
        return props.itemKey(item.data, index)
      }
      return item.data[props.itemKey] || index
    }

    // 处理滚动
    const handleScroll = (event) => {
      scrollTop.value = event.target.scrollTop
      emit('scroll', {
        scrollTop: scrollTop.value,
        scrollLeft: event.target.scrollLeft
      })
    }

    // 滚动到指定位置
    const scrollTo = (index) => {
      if (containerRef.value) {
        const targetScrollTop = index * props.itemHeight
        containerRef.value.scrollTop = targetScrollTop
        scrollTop.value = targetScrollTop
      }
    }

    // 滚动到顶部
    const scrollToTop = () => {
      scrollTo(0)
    }

    // 滚动到底部
    const scrollToBottom = () => {
      scrollTo(props.items.length - 1)
    }

    return {
      scrollTop,
      containerRef,
      visibleItems,
      containerStyle,
      phantomStyle,
      contentStyle,
      getItemStyle,
      getItemKey,
      handleScroll,
      scrollTo,
      scrollToTop,
      scrollToBottom
    }
  }
}
</script>

<style scoped>
.virtual-list {
  position: relative;
  overflow: auto;
}

.virtual-list-phantom {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: -1;
}

.virtual-list-content {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
}

.virtual-list-item {
  padding: 0 16px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
}

.virtual-list-item:hover {
  background: #fafafa;
}

.virtual-list-item:last-child {
  border-bottom: none;
}

/* 滚动条样式 */
.virtual-list::-webkit-scrollbar {
  width: 8px;
}

.virtual-list::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

.virtual-list::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.virtual-list::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style>
