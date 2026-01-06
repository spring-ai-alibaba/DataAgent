import * as echarts from 'echarts';
import { BaseChart } from './BaseChart';
import { COLOR_PANEL } from './ChartFactory';

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
    const seriesData = yAxes.map(yAxis => ({
      name: yAxis.name,
      type: 'line',
      smooth: true,
      data: this.data.map(item => {
        const value = item[yAxis.value];
        return isNaN(Number(value)) ? value : Number(value);
      }),
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
      color: COLOR_PANEL,
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
