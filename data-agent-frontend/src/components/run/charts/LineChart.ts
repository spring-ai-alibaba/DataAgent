/*
 * Copyright 2024-2025 the original author or authors.
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

import * as echarts from 'echarts';
import { BaseChart, generateUniqueColors } from './BaseChart';

export class LineChart extends BaseChart {
  private chartInstance: echarts.ECharts | null = null;

  constructor(id: string, name: string) {
    super(id, name);
  }

  render(): void {
    if (!this.data || this.data.length === 0) {
      return;
    }

    const container = document.getElementById(this.id);
    if (!container) {
      return;
    }

    // 获取x轴和y轴数据
    const xAxis = this.axis.find(axis => axis.type === 'x');
    const yAxes = this.axis.filter(axis => axis.type === 'y');

    if (!xAxis || yAxes.length === 0) {
      return;
    }

    const xAxisData = this.data.map(item => item[xAxis.value]);
    const colors: string[] = generateUniqueColors(yAxes.length);
    const seriesData = yAxes.map((yAxis, index) => ({
      name: yAxis.name,
      type: 'line',
      smooth: true,
      data: this.data.map(item => {
        const value = item[yAxis.value];
        return isNaN(Number(value)) ? value : Number(value);
      }),
      // 只对前6个图例使用指定颜色，超出部分使用ECharts默认颜色
      color: colors[index],
    }));

    if (!this.chartInstance) {
      this.chartInstance = echarts.init(container);
    }

    const option: echarts.EChartsOption = {
      title: {
        text: this._name || '曲线图',
        left: 'center',
      },
      tooltip: {
        trigger: 'axis',
      },
      legend: {
        orient: 'horizontal',
        bottom: 0,
      },
      xAxis: {
        type: 'category',
        data: xAxisData,
        axisLabel: {
          rotate: xAxisData.length > 10 ? 45 : 0,
        },
      },
      yAxis: {
        type: 'value',
      },
      series: seriesData,
    };

    this.chartInstance.setOption(option);
  }

  destroy(): void {
    if (this.chartInstance) {
      this.chartInstance.dispose();
      this.chartInstance = null;
    }
  }

  resize(): void {
    if (this.chartInstance) {
      this.chartInstance.resize();
    }
  }
}
