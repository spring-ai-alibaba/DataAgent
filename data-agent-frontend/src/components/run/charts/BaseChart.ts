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

export interface ChartAxis {
  name: string;
  value: string;
  type?: 'x' | 'y' | 'series';
}

export interface ChartData {
  [key: string]: any;
}

export type ChartTypes = 'table' | 'bar' | 'column' | 'line' | 'pie';

export abstract class BaseChart {
  id: string;
  _name: string = 'base-chart';
  axis: Array<ChartAxis> = [];
  data: Array<ChartData> = [];

  constructor(id: string, name: string) {
    this.id = id;
    this._name = name;
  }

  init(axis: Array<ChartAxis>, data: Array<ChartData>): void {
    this.axis = axis;
    this.data = data;
  }

  abstract render(): void;

  abstract destroy(): void;

  abstract resize(): void;
}
