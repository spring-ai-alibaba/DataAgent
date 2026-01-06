import { BaseChart, ChartTypes } from './BaseChart';
import { PieChart } from './PieChart';
import { BarChart } from './BarChart';
import { LineChart } from './LineChart';

export const COLOR_PANEL = [
  '#09b0d3',
  '#0082fc',
  '#fdd845',
  '#22ed7c',
  '#1d27c9',
  '#05f8d6',
  '#f9e264',
  '#f47a75',
  '#009db2',
];

export class ChartFactory {
  static createChart(chartType: ChartTypes, id: string, name: string): BaseChart | null {
    switch (chartType) {
      case 'pie':
        return new PieChart(id, name);
      case 'bar':
      case 'column':
        return new BarChart(id, name);
      case 'line':
        return new LineChart(id, name);
      default:
        console.error(`Unsupported chart type: ${chartType}`);
        return null;
    }
  }
}
