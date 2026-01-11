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

<script setup lang="ts">
  import { onMounted, onUnmounted, ref, watch, computed } from 'vue';
  import type { ResultData } from '@/services/resultSet';
  import type { ResultDisplayStyleBO } from '@/services/resultSet';
  import { ChartFactory } from './charts/ChartFactory';
  import { BaseChart, ChartAxis, ChartTypes } from './charts/BaseChart';

  const props = defineProps<{
    resultData: ResultData;
  }>();

  const chartRef = ref<HTMLDivElement>();
  let chartInstance: BaseChart | null = null;

  // 生成唯一的图表ID
  const chartId = `chart-${Math.random().toString(36).substr(2, 9)}`;

  const initChart = () => {
    if (
      !chartRef.value ||
      !props.resultData.resultSet.data ||
      props.resultData.resultSet.data.length === 0
    ) {
      return;
    }

    // 销毁现有图表实例
    if (chartInstance) {
      chartInstance.destroy();
      chartInstance = null;
    }

    // 设置DOM元素的ID
    chartRef.value.id = chartId;

    // 解析图表类型
    const chartType = (props.resultData.displayStyle?.type as ChartTypes) || 'bar';
    const chartTitle = props.resultData.displayStyle?.title || '数据可视化';

    // 创建图表轴配置
    const axes: ChartAxis[] = [];

    // 添加x轴
    if (props.resultData.displayStyle?.x) {
      axes.push({
        name: props.resultData.displayStyle.x,
        value: props.resultData.displayStyle.x,
        type: 'x',
      });
    }

    // 添加y轴
    if (props.resultData.displayStyle?.y && Array.isArray(props.resultData.displayStyle.y)) {
      props.resultData.displayStyle.y.forEach(yField => {
        axes.push({
          name: yField,
          value: yField,
          type: 'y',
        });
      });
    }

    // 如果没有指定轴，使用默认轴
    if (
      axes.length === 0 &&
      props.resultData.resultSet.column &&
      props.resultData.resultSet.column.length > 0
    ) {
      axes.push({
        name: props.resultData.resultSet.column[0],
        value: props.resultData.resultSet.column[0],
        type: 'x',
      });

      if (props.resultData.resultSet.column.length > 1) {
        axes.push({
          name: props.resultData.resultSet.column[1],
          value: props.resultData.resultSet.column[1],
          type: 'y',
        });
      }
    }

    // 创建图表实例
    chartInstance = ChartFactory.createChart(chartType, chartId, chartTitle);

    if (chartInstance) {
      // 初始化图表数据
      chartInstance.init(axes, props.resultData.resultSet.data);
      // 渲染图表
      chartInstance.render();
    }
  };

  const handleResize = () => {
    // 窗口大小变化时调用图表实例的resize方法
    if (chartInstance) {
      chartInstance.resize();
    }
  };

  onMounted(() => {
    initChart();
    window.addEventListener('resize', handleResize);
  });

  onUnmounted(() => {
    if (chartInstance) {
      chartInstance.destroy();
      chartInstance = null;
    }
    window.removeEventListener('resize', handleResize);
  });

  // 使用计算属性提取图表渲染所需的关键数据
  const chartKeyData = computed(() => {
    const displayStyle: ResultDisplayStyleBO = props.resultData.displayStyle || {};
    const data = props.resultData.resultSet.data || [];
    return {
      type: displayStyle?.type,
      title: displayStyle?.title,
      x: displayStyle?.x,
      y: displayStyle?.y,
      data: JSON.stringify(data), // 使用JSON.stringify来深度比较数据数组
    };
  });

  watch(
    chartKeyData,
    (newData, oldData) => {
      // 比较新旧数据是否相同，只有不同时才执行initChart
      const dataChanged = JSON.stringify(newData) !== JSON.stringify(oldData);
      if (dataChanged) {
        initChart();
      }
    },
    { deep: false, immediate: false },
  ); // 不需要立即执行，组件挂载时已经初始化
</script>

<template>
  <div ref="chartRef" class="chart-container"></div>
</template>

<style scoped>
  .chart-container {
    width: 100%;
    max-width: 100%;
    height: 400px;
    max-height: 400px;
    margin: 0 auto;
  }
</style>
