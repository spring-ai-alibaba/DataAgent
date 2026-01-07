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

export class PieChart extends BaseChart {
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

    // 获取饼图的name和value轴
    const nameAxis = this.axis.find(axis => axis.type === 'x') || this.axis[0];
    const valueAxis = this.axis.find(axis => axis.type === 'y') || this.axis[1];

    if (!nameAxis || !valueAxis) {
      return;
    }

    // 生成足够的唯一颜色
    const colors: string[] = generateUniqueColors(this.data.length);

    const pieData = this.data.map((item, index) => ({
      name: item[nameAxis.value] || `Item ${index}`,
      value: parseFloat(item[valueAxis.value]) || 0,
      itemStyle: { color: colors[index] },
    }));

    if (!this.chartInstance) {
      this.chartInstance = echarts.init(container);
    }

    const option: echarts.EChartsOption = {
      title: {
        text: this._name || '饼图',
        left: 'center',
      },
      tooltip: {
        trigger: 'item',
        formatter: '{a} <br/>{b}: {c} ({d}%)',
      },
      legend: {
        orient: 'horizontal',
        bottom: 0,
      },
      series: [
        {
          name: this._name || '饼图',
          type: 'pie',
          radius: '50%',
          data: pieData,
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.5)',
            },
          },
        },
      ],
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
