import * as echarts from 'echarts';
import { BaseChart } from './BaseChart';
import { COLOR_PANEL } from './ChartFactory';

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

    const pieData = this.data.map((item, index) => ({
      name: item[nameAxis.value] || `Item ${index}`,
      value: parseFloat(item[valueAxis.value]) || 0,
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
      color: COLOR_PANEL,
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
