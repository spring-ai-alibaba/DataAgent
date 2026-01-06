<script setup lang="ts">
  import { onMounted, onUnmounted, ref, watch, computed } from 'vue';
  import type { ResultSetData } from '@/services/resultSet';
  import { ChartFactory } from './charts/ChartFactory';
  import { BaseChart, ChartAxis, ChartTypes } from './charts/BaseChart';

  const props = defineProps<{
    resultSetData: ResultSetData;
  }>();

  const chartRef = ref<HTMLDivElement>();
  let chartInstance: BaseChart | null = null;

  // 生成唯一的图表ID
  const chartId = `chart-${Math.random().toString(36).substr(2, 9)}`;

  const initChart = () => {
    console.log('初始化图表');
    if (!chartRef.value || !props.resultSetData.data || props.resultSetData.data.length === 0) {
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
    const chartType = (props.resultSetData.type as ChartTypes) || 'bar';
    const chartTitle = props.resultSetData.title || '数据可视化';

    // 创建图表轴配置
    const axes: ChartAxis[] = [];

    // 添加x轴
    if (props.resultSetData.x) {
      axes.push({
        name: props.resultSetData.x,
        value: props.resultSetData.x,
        type: 'x',
      });
    }

    // 添加y轴
    if (props.resultSetData.y && Array.isArray(props.resultSetData.y)) {
      props.resultSetData.y.forEach(yField => {
        axes.push({
          name: yField,
          value: yField,
          type: 'y',
        });
      });
    }

    // 如果没有指定轴，使用默认轴
    if (axes.length === 0 && props.resultSetData.column && props.resultSetData.column.length > 0) {
      axes.push({
        name: props.resultSetData.column[0],
        value: props.resultSetData.column[0],
        type: 'x',
      });

      if (props.resultSetData.column.length > 1) {
        axes.push({
          name: props.resultSetData.column[1],
          value: props.resultSetData.column[1],
          type: 'y',
        });
      }
    }

    // 创建图表实例
    chartInstance = ChartFactory.createChart(chartType, chartId, chartTitle);

    if (chartInstance) {
      // 初始化图表数据
      chartInstance.init(axes, props.resultSetData.data);
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
    const { type, title, x, y, data } = props.resultSetData;
    return {
      type,
      title,
      x,
      y,
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
