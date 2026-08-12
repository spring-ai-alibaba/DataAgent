#!/usr/bin/env bash

# Copyright 2026 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -eu

failed=0

check_pattern() {
	label=$1
	pattern=$2
	shift 2

	if matches=$(git grep -nE "$pattern" -- "$@" 2>/dev/null); then
		printf 'Test trust violation: %s\n%s\n' "$label" "$matches"
		failed=1
	fi
}

java_tests=data-agent-management/src/test
frontend_tests=':(glob)data-agent-frontend-nuxt/app/**/*.test.ts'
runtime_exception_helper=':(exclude)data-agent-management/src/test/java/com/alibaba/cloud/ai/dataagent/support/ExceptionTestSupport.java'

check_pattern 'disabled Java test' '@(Disabled|Ignore)' "$java_tests"
check_pattern 'conditional Java test assumption' 'assume(True|False|That)[[:space:]]*\(' "$java_tests"
check_pattern 'lenient Mockito configuration' 'Strictness\.LENIENT|Mockito\.lenient|lenient[[:space:]]*\(' "$java_tests"
check_pattern 'constant assertion' 'assertTrue[[:space:]]*\([[:space:]]*true[[:space:]]*\)|assertFalse[[:space:]]*\([[:space:]]*false[[:space:]]*\)' "$java_tests"
check_pattern 'sleep-based test synchronization' 'Thread\.sleep[[:space:]]*\(' "$java_tests"
check_pattern 'broad exception assertion' 'assertThrows[[:space:]]*\([[:space:]]*(Exception|RuntimeException)\.class' "$java_tests" "$runtime_exception_helper"
check_pattern 'disabled or focused frontend test' '\.(skip|skipIf|runIf|only|todo)[[:space:]]*\(' "$frontend_tests"

if [ "$failed" -ne 0 ]; then
	exit 1
fi

printf 'Test trust static checks passed.\n'
