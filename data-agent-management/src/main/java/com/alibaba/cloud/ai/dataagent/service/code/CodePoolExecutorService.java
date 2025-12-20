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

package com.alibaba.cloud.ai.dataagent.service.code;

/**
 * 运行Python任务的容器池接口
 *
 * @author vlsmb
 * @since 2025/7/12
 */
public interface CodePoolExecutorService {

	TaskResponse runTask(TaskRequest request);

	record TaskRequest(String code, String input, String requirement) {

	}

	record TaskResponse(boolean isSuccess, boolean executionSuccessButResultFailed, String stdOut, String stdErr,
			String exceptionMsg, boolean isRetryable, String friendlyMessage) {

		// 执行运行代码任务时发生异常
		public static TaskResponse exception(String msg) {
			return new TaskResponse(false, false, null, null, "执行任务时发生异常: " + msg, true,
					"代码执行遇到系统异常，请稍后重试");
		}

		// 执行运行代码任务成功，并且代码正常返回
		public static TaskResponse success(String stdOut) {
			return new TaskResponse(true, false, stdOut, null, null, false, null);
		}

		// 执行运行代码任务成功，但是代码异常返回
		public static TaskResponse failure(String stdOut, String stdErr) {
			boolean retryable = isErrorRetryable(stdErr);
			String friendlyMsg = generateFriendlyErrorMessage(stdErr);
			return new TaskResponse(false, true, stdOut, stdErr, "StdErr: " + stdErr, retryable, friendlyMsg);
		}

		/**
		 * 判断错误是否可重试
		 */
		private static boolean isErrorRetryable(String stderr) {
			if (stderr == null || stderr.isEmpty()) {
				return true;
			}

			String errorLower = stderr.toLowerCase();

			// 语法错误不可重试
			if (errorLower.contains("syntaxerror") || errorLower.contains("indentationerror")) {
				return false;
			}

			// 找不到变量、属性、键等错误不可重试
			if (errorLower.contains("nameerror") || errorLower.contains("attributeerror")
					|| errorLower.contains("keyerror")) {
				return false;
			}

			// 找不到文件或模块错误不可重试
			if (errorLower.contains("filenotfounderror") || errorLower.contains("modulenotfounderror")
					|| errorLower.contains("importerror")) {
				return false;
			}

			// 类型错误、值错误通常是逻辑问题，不可重试
			if (errorLower.contains("typeerror") || errorLower.contains("valueerror")) {
				return false;
			}

			// 其他错误默认可重试
			return true;
		}

		/**
		 * 生成友好的中文错误消息
		 */
		private static String generateFriendlyErrorMessage(String stderr) {
			if (stderr == null || stderr.isEmpty()) {
				return "代码执行失败，未返回具体错误信息";
			}

			String errorLower = stderr.toLowerCase();

			// 语法错误
			if (errorLower.contains("syntaxerror")) {
				return "Python代码存在语法错误，请检查代码格式是否正确";
			}

			if (errorLower.contains("indentationerror")) {
				return "Python代码缩进错误，请检查代码缩进是否正确";
			}

			// 找不到变量、属性、键
			if (errorLower.contains("nameerror")) {
				return "代码中使用了未定义的变量或函数，请检查变量名是否正确";
			}

			if (errorLower.contains("keyerror")) {
				return "尝试访问不存在的数据键，请检查数据结构和键名";
			}

			if (errorLower.contains("attributeerror")) {
				return "尝试访问对象不存在的属性或方法，请检查对象类型和属性名";
			}

			// 找不到文件或模块
			if (errorLower.contains("filenotfounderror")) {
				return "找不到指定的文件，请检查文件路径是否正确";
			}

			if (errorLower.contains("modulenotfounderror") || errorLower.contains("importerror")) {
				return "找不到指定的Python模块，可能需要安装相应的依赖包";
			}

			// 类型错误、值错误
			if (errorLower.contains("typeerror")) {
				return "数据类型错误，请检查函数参数或操作的数据类型是否正确";
			}

			if (errorLower.contains("valueerror")) {
				return "数据值错误，请检查传入的数据值是否符合要求";
			}

			// 索引错误
			if (errorLower.contains("indexerror")) {
				return "数组索引超出范围，请检查数据长度和索引值";
			}

			// 零除错误
			if (errorLower.contains("zerodivisionerror")) {
				return "除数为零错误，请检查计算逻辑";
			}

			// 超时错误
			if (errorLower.contains("timeout") || errorLower.contains("killed")) {
				return "代码执行超时，请优化代码性能或检查是否存在死循环";
			}

			// 默认返回原始错误信息
			return "代码执行失败: " + (stderr.length() > 200 ? stderr.substring(0, 200) + "..." : stderr);
		}

		@Override
		public String toString() {
			return "TaskResponse{" + "isSuccess=" + isSuccess + ", stdOut='" + stdOut + '\'' + ", stdErr='" + stdErr
					+ '\'' + ", exceptionMsg='" + exceptionMsg + '\'' + ", isRetryable=" + isRetryable
					+ ", friendlyMessage='" + friendlyMessage + '\'' + '}';
		}
	}

	enum State {

		READY, RUNNING, REMOVING

	}

}
