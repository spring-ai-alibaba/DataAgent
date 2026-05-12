/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeUtilTest {

	private static final LocalDate FIXED_DATE = LocalDate.of(2026, 4, 3);

	// --- getYearEx ---

	@Test
	void getYearEx_thisYear() {
		assertEquals("2026年", DateTimeUtil.getYearEx(FIXED_DATE, "今年", true));
	}

	@Test
	void getYearEx_lastYear() {
		assertEquals("2025年", DateTimeUtil.getYearEx(FIXED_DATE, "去年", false));
	}

	@Test
	void getYearEx_yearBeforeLast() {
		assertEquals("2024年", DateTimeUtil.getYearEx(FIXED_DATE, "前年", false));
	}

	@Test
	void getYearEx_nextYear() {
		assertEquals("2027年", DateTimeUtil.getYearEx(FIXED_DATE, "明年", false));
	}

	@Test
	void getYearEx_yearAfterNext() {
		assertEquals("2028年", DateTimeUtil.getYearEx(FIXED_DATE, "后年", false));
	}

	// --- getMonthEx ---

	@Test
	void getMonthEx_thisMonth() {
		assertEquals("2026年04月", DateTimeUtil.getMonthEx(FIXED_DATE, "本月"));
	}

	@Test
	void getMonthEx_lastMonth() {
		assertEquals("2026年03月", DateTimeUtil.getMonthEx(FIXED_DATE, "上月"));
	}

	@Test
	void getMonthEx_twoMonthsAgo() {
		assertEquals("2026年02月", DateTimeUtil.getMonthEx(FIXED_DATE, "上上月"));
	}

	@Test
	void getMonthEx_nextMonth() {
		assertEquals("2026年05月", DateTimeUtil.getMonthEx(FIXED_DATE, "下月"));
	}

	@Test
	void getMonthEx_lastYearThisMonth() {
		assertEquals("2025年04月", DateTimeUtil.getMonthEx(FIXED_DATE, "去年本月"));
	}

	// --- getDayEx ---

	@Test
	void getDayEx_today() {
		assertEquals("2026年04月03日", DateTimeUtil.getDayEx(FIXED_DATE, "今天"));
	}

	@Test
	void getDayEx_yesterday() {
		assertEquals("2026年04月02日", DateTimeUtil.getDayEx(FIXED_DATE, "昨天"));
	}

	@Test
	void getDayEx_dayBeforeYesterday() {
		assertEquals("2026年04月01日", DateTimeUtil.getDayEx(FIXED_DATE, "前天"));
	}

	@Test
	void getDayEx_tomorrow() {
		assertEquals("2026年04月04日", DateTimeUtil.getDayEx(FIXED_DATE, "明天"));
	}

	@Test
	void getDayEx_dayAfterTomorrow() {
		assertEquals("2026年04月05日", DateTimeUtil.getDayEx(FIXED_DATE, "后天"));
	}

	@Test
	void getDayEx_lastMonthToday() {
		assertEquals("2026年03月03日", DateTimeUtil.getDayEx(FIXED_DATE, "上月今天"));
	}

	@Test
	void getDayEx_twoMonthsAgoToday() {
		assertEquals("2026年02月03日", DateTimeUtil.getDayEx(FIXED_DATE, "上上月今天"));
	}

	// --- getWeekDayEx ---

	@Test
	void getWeekDayEx_firstDayOfWeek() {
		String result = DateTimeUtil.getWeekDayEx(FIXED_DATE, 1);
		assertEquals("2026年03月30日", result);
	}

	@Test
	void getWeekDayEx_fifthDayOfWeek() {
		String result = DateTimeUtil.getWeekDayEx(FIXED_DATE, 5);
		assertEquals("2026年04月03日", result);
	}

	// --- getGeneralWeekDayEx ---

	@Test
	void getGeneralWeekDayEx_thisWeek() {
		String result = DateTimeUtil.getGeneralWeekDayEx(FIXED_DATE, "本周", 1);
		assertEquals("2026年03月30日", result);
	}

	@Test
	void getGeneralWeekDayEx_lastWeek() {
		String result = DateTimeUtil.getGeneralWeekDayEx(FIXED_DATE, "上周", 1);
		assertEquals("2026年03月23日", result);
	}

	@Test
	void getGeneralWeekDayEx_twoWeeksAgo() {
		String result = DateTimeUtil.getGeneralWeekDayEx(FIXED_DATE, "上上周", 1);
		assertEquals("2026年03月16日", result);
	}

	@Test
	void getGeneralWeekDayEx_nextWeek() {
		String result = DateTimeUtil.getGeneralWeekDayEx(FIXED_DATE, "下周", 1);
		assertEquals("2026年04月06日", result);
	}

	@Test
	void getGeneralWeekDayEx_twoWeeksLater() {
		String result = DateTimeUtil.getGeneralWeekDayEx(FIXED_DATE, "下下周", 1);
		assertEquals("2026年04月13日", result);
	}

	// --- getWeekEx ---

	@Test
	void getWeekEx_thisWeek() {
		String result = DateTimeUtil.getWeekEx(FIXED_DATE, "本周");
		assertTrue(result.contains("至"));
	}

	@Test
	void getWeekEx_lastWeek() {
		String result = DateTimeUtil.getWeekEx(FIXED_DATE, "上周");
		assertTrue(result.contains("至"));
	}

	@Test
	void getWeekEx_twoWeeksAgo() {
		String result = DateTimeUtil.getWeekEx(FIXED_DATE, "上上周");
		assertTrue(result.contains("至"));
	}

	@Test
	void getWeekEx_nextWeek() {
		String result = DateTimeUtil.getWeekEx(FIXED_DATE, "下周");
		assertTrue(result.contains("至"));
	}

	@Test
	void getWeekEx_twoWeeksLater() {
		String result = DateTimeUtil.getWeekEx(FIXED_DATE, "下下周");
		assertTrue(result.contains("至"));
	}

	// --- getQuarterEx ---

	@Test
	void getQuarterEx_thisQuarter() {
		String result = DateTimeUtil.getQuarterEx(FIXED_DATE, "本季度");
		assertEquals("2026年第2季度", result);
	}

	@Test
	void getQuarterEx_lastQuarter() {
		String result = DateTimeUtil.getQuarterEx(FIXED_DATE, "上季度");
		assertEquals("2026年第1季度", result);
	}

	@Test
	void getQuarterEx_nextQuarter() {
		String result = DateTimeUtil.getQuarterEx(FIXED_DATE, "下季度");
		assertEquals("2026年第3季度", result);
	}

	@Test
	void getQuarterEx_lastYearThisQuarter() {
		String result = DateTimeUtil.getQuarterEx(FIXED_DATE, "去年本季度");
		assertEquals("2025年第2季度", result);
	}

	@Test
	void getQuarterEx_q1() {
		LocalDate jan = LocalDate.of(2026, 1, 15);
		String result = DateTimeUtil.getQuarterEx(jan, "本季度");
		assertEquals("2026年第1季度", result);
	}

	@Test
	void getQuarterEx_q4_lastQuarter() {
		LocalDate oct = LocalDate.of(2026, 10, 15);
		String result = DateTimeUtil.getQuarterEx(oct, "上季度");
		assertEquals("2026年第3季度", result);
	}

	@Test
	void getQuarterEx_q4_nextQuarter_wrapsYear() {
		LocalDate oct = LocalDate.of(2026, 10, 15);
		String result = DateTimeUtil.getQuarterEx(oct, "下季度");
		assertEquals("2027年第1季度", result);
	}

	@Test
	void getQuarterEx_q1_lastQuarter_wrapsYear() {
		LocalDate jan = LocalDate.of(2026, 1, 15);
		String result = DateTimeUtil.getQuarterEx(jan, "上季度");
		assertEquals("2025年第4季度", result);
	}

	// --- getRecentNYear ---

	@Test
	void getRecentNYear() {
		String result = DateTimeUtil.getRecentNYear(FIXED_DATE, 2);
		assertEquals("2024年04月03日至2026年04月03日", result);
	}

	// --- getRecentNMonth ---

	@Test
	void getRecentNMonth() {
		String result = DateTimeUtil.getRecentNMonth(FIXED_DATE, 3);
		assertEquals("2026年01月03日至2026年04月03日", result);
	}

	// --- getRecentNWeek ---

	@Test
	void getRecentNWeek() {
		String result = DateTimeUtil.getRecentNWeek(FIXED_DATE, 2);
		assertEquals("2026年03月20日至2026年04月03日", result);
	}

	// --- getRecentNDay ---

	@Test
	void getRecentNDay() {
		String result = DateTimeUtil.getRecentNDay(FIXED_DATE, 7);
		assertEquals("2026年03月27日至2026年04月03日", result);
	}

	// --- getRecentNDayWithoutToday ---

	@Test
	void getRecentNDayWithoutToday() {
		String result = DateTimeUtil.getRecentNDayWithoutToday(FIXED_DATE, 7);
		assertEquals("2026年03月27日至2026年04月02日", result);
	}

	// --- getRecentNCompleteYear ---

	@Test
	void getRecentNCompleteYear_notLastDayOfYear() {
		String result = DateTimeUtil.getRecentNCompleteYear(FIXED_DATE, 2);
		assertTrue(result.contains("至"));
		assertTrue(result.contains("2025年12月31日"));
	}

	@Test
	void getRecentNCompleteYear_lastDayOfYear() {
		LocalDate dec31 = LocalDate.of(2026, 12, 31);
		String result = DateTimeUtil.getRecentNCompleteYear(dec31, 2);
		assertTrue(result.contains("至2026年12月31日"));
	}

	// --- getRecentNCompleteMonth ---

	@Test
	void getRecentNCompleteMonth_notLastDayOfMonth() {
		String result = DateTimeUtil.getRecentNCompleteMonth(FIXED_DATE, 3);
		assertTrue(result.contains("至"));
		assertTrue(result.contains("2026年03月31日"));
	}

	@Test
	void getRecentNCompleteMonth_lastDayOfMonth() {
		LocalDate lastDay = LocalDate.of(2026, 3, 31);
		String result = DateTimeUtil.getRecentNCompleteMonth(lastDay, 2);
		assertTrue(result.contains("至2026年03月31日"));
	}

	// --- getRecentNCompleteQuarter ---

	@Test
	void getRecentNCompleteQuarter_q2() {
		String result = DateTimeUtil.getRecentNCompleteQuarter(FIXED_DATE, 2);
		assertTrue(result.contains("至"));
		assertTrue(result.contains("2026年03月31日"));
	}

	@Test
	void getRecentNCompleteQuarter_q3() {
		LocalDate jul = LocalDate.of(2026, 7, 15);
		String result = DateTimeUtil.getRecentNCompleteQuarter(jul, 1);
		assertTrue(result.contains("至2026年06月30日"));
	}

	@Test
	void getRecentNCompleteQuarter_q4() {
		LocalDate oct = LocalDate.of(2026, 10, 15);
		String result = DateTimeUtil.getRecentNCompleteQuarter(oct, 1);
		assertTrue(result.contains("至2026年09月30日"));
	}

	@Test
	void getRecentNCompleteQuarter_q1() {
		LocalDate jan = LocalDate.of(2026, 1, 15);
		String result = DateTimeUtil.getRecentNCompleteQuarter(jan, 1);
		assertTrue(result.contains("至2025年12月31日"));
	}

	// --- getRecentNCompleteWeek ---

	@Test
	void getRecentNCompleteWeek_notSunday() {
		String result = DateTimeUtil.getRecentNCompleteWeek(FIXED_DATE, 2);
		assertTrue(result.contains("至"));
	}

	@Test
	void getRecentNCompleteWeek_onSunday() {
		LocalDate sunday = LocalDate.of(2026, 4, 5);
		String result = DateTimeUtil.getRecentNCompleteWeek(sunday, 2);
		assertTrue(result.contains("至2026年04月05日"));
	}

	// --- getRecentNQuarterWithCurrent ---

	@Test
	void getRecentNQuarterWithCurrent_q2() {
		String result = DateTimeUtil.getRecentNQuarterWithCurrent(FIXED_DATE, 2);
		assertTrue(result.contains("至2026年06月30日"));
	}

	@Test
	void getRecentNQuarterWithCurrent_q1() {
		LocalDate jan = LocalDate.of(2026, 1, 15);
		String result = DateTimeUtil.getRecentNQuarterWithCurrent(jan, 2);
		assertTrue(result.contains("至2026年03月31日"));
	}

	@Test
	void getRecentNQuarterWithCurrent_q3() {
		LocalDate jul = LocalDate.of(2026, 7, 15);
		String result = DateTimeUtil.getRecentNQuarterWithCurrent(jul, 2);
		assertTrue(result.contains("至2026年09月30日"));
	}

	@Test
	void getRecentNQuarterWithCurrent_q4() {
		LocalDate oct = LocalDate.of(2026, 10, 15);
		String result = DateTimeUtil.getRecentNQuarterWithCurrent(oct, 2);
		assertTrue(result.contains("至2026年12月31日"));
	}

	// --- getMonthLastDayEx ---

	@Test
	void getMonthLastDayEx_thisMonth() {
		String result = DateTimeUtil.getMonthLastDayEx(FIXED_DATE, "本月");
		assertEquals("2026年04月30日", result);
	}

	@Test
	void getMonthLastDayEx_lastMonth() {
		String result = DateTimeUtil.getMonthLastDayEx(FIXED_DATE, "上月");
		assertEquals("2026年03月31日", result);
	}

	// --- getGeneralYearMonthLastDayEx ---

	@Test
	void getGeneralYearMonthLastDayEx_thisYear() {
		String result = DateTimeUtil.getGeneralYearMonthLastDayEx(FIXED_DATE, "今年", 2);
		assertEquals("2026年02月28日", result);
	}

	@Test
	void getGeneralYearMonthLastDayEx_lastYear() {
		String result = DateTimeUtil.getGeneralYearMonthLastDayEx(FIXED_DATE, "去年", 2);
		assertEquals("2025年02月28日", result);
	}

	@Test
	void getGeneralYearMonthLastDayEx_yearBeforeLast() {
		String result = DateTimeUtil.getGeneralYearMonthLastDayEx(FIXED_DATE, "前年", 2);
		assertEquals("2024年02月29日", result);
	}

	@Test
	void getGeneralYearMonthLastDayEx_nextYear() {
		String result = DateTimeUtil.getGeneralYearMonthLastDayEx(FIXED_DATE, "明年", 2);
		assertEquals("2027年02月28日", result);
	}

	@Test
	void getGeneralYearMonthLastDayEx_yearAfterNext() {
		String result = DateTimeUtil.getGeneralYearMonthLastDayEx(FIXED_DATE, "后年", 2);
		assertEquals("2028年02月29日", result);
	}

	// --- getSpecificYearWeekEx ---

	@Test
	void getSpecificYearWeekEx() {
		String result = DateTimeUtil.getSpecificYearWeekEx(FIXED_DATE, 2026, 5);
		assertTrue(result.contains("至"));
	}

	// --- getGeneralYearWeekEx ---

	@Test
	void getGeneralYearWeekEx_thisYear() {
		String result = DateTimeUtil.getGeneralYearWeekEx(FIXED_DATE, "今年", 5);
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralYearWeekEx_lastYear() {
		String result = DateTimeUtil.getGeneralYearWeekEx(FIXED_DATE, "去年", 5);
		assertTrue(result.contains("2025"));
	}

	@Test
	void getGeneralYearWeekEx_yearBeforeLast() {
		String result = DateTimeUtil.getGeneralYearWeekEx(FIXED_DATE, "前年", 5);
		assertTrue(result.contains("2024"));
	}

	@Test
	void getGeneralYearWeekEx_nextYear() {
		String result = DateTimeUtil.getGeneralYearWeekEx(FIXED_DATE, "明年", 5);
		assertTrue(result.contains("2027"));
	}

	@Test
	void getGeneralYearWeekEx_yearAfterNext() {
		String result = DateTimeUtil.getGeneralYearWeekEx(FIXED_DATE, "后年", 5);
		assertTrue(result.contains("2028"));
	}

	// --- getGeneralMonthWeekEx ---

	@Test
	void getGeneralMonthWeekEx_thisMonth() {
		String result = DateTimeUtil.getGeneralMonthWeekEx(FIXED_DATE, "本月", 1);
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthWeekEx_lastMonth() {
		String result = DateTimeUtil.getGeneralMonthWeekEx(FIXED_DATE, "上月", 1);
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthWeekEx_lastMonth_januaryWrap() {
		LocalDate jan = LocalDate.of(2026, 1, 15);
		String result = DateTimeUtil.getGeneralMonthWeekEx(jan, "上月", 1);
		assertTrue(result.contains("2025"));
	}

	// --- getSpecificYearMonthWeekEx ---

	@Test
	void getSpecificYearMonthWeekEx() {
		String result = DateTimeUtil.getSpecificYearMonthWeekEx(FIXED_DATE, 2026, 4, 1);
		assertTrue(result.contains("至"));
	}

	// --- getGeneralYearMonthWeekEx ---

	@Test
	void getGeneralYearMonthWeekEx_allYears() {
		for (String yearEx : List.of("今年", "去年", "前年", "明年", "后年")) {
			String result = DateTimeUtil.getGeneralYearMonthWeekEx(FIXED_DATE, yearEx, 4, 1);
			assertTrue(result.contains("至"), "Failed for yearEx=" + yearEx);
		}
	}

	// --- getSpecificYearMonthCompleteWeekEx ---

	@Test
	void getSpecificYearMonthCompleteWeekEx() {
		String result = DateTimeUtil.getSpecificYearMonthCompleteWeekEx(FIXED_DATE, 2026, 4, 1);
		assertTrue(result.contains("至"));
	}

	@Test
	void getSpecificYearMonthCompleteWeekEx_exceedsMonth() {
		String result = DateTimeUtil.getSpecificYearMonthCompleteWeekEx(FIXED_DATE, 2026, 2, 5);
		assertEquals("", result);
	}

	// --- getGeneralYearMonthCompleteWeekEx ---

	@Test
	void getGeneralYearMonthCompleteWeekEx_allYears() {
		for (String yearEx : List.of("今年", "去年", "前年", "明年", "后年")) {
			String result = DateTimeUtil.getGeneralYearMonthCompleteWeekEx(FIXED_DATE, yearEx, 4, 1);
			assertTrue(result.contains("至"), "Failed for yearEx=" + yearEx);
		}
	}

	// --- getGeneralMonthCompleteWeekEx ---

	@Test
	void getGeneralMonthCompleteWeekEx_thisMonth() {
		String result = DateTimeUtil.getGeneralMonthCompleteWeekEx(FIXED_DATE, "本月", 1);
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthCompleteWeekEx_lastMonth() {
		String result = DateTimeUtil.getGeneralMonthCompleteWeekEx(FIXED_DATE, "上月", 1);
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthCompleteWeekEx_twoMonthsAgo() {
		String result = DateTimeUtil.getGeneralMonthCompleteWeekEx(FIXED_DATE, "上上月", 1);
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthCompleteWeekEx_nextMonth() {
		String result = DateTimeUtil.getGeneralMonthCompleteWeekEx(FIXED_DATE, "下月", 1);
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthCompleteWeekEx_januaryWrap() {
		LocalDate jan = LocalDate.of(2026, 1, 15);
		String result = DateTimeUtil.getGeneralMonthCompleteWeekEx(jan, "上月", 1);
		assertTrue(result.contains("2025"));
	}

	@Test
	void getGeneralMonthCompleteWeekEx_decemberWrap() {
		LocalDate dec = LocalDate.of(2026, 12, 15);
		String result = DateTimeUtil.getGeneralMonthCompleteWeekEx(dec, "下月", 1);
		assertTrue(result.contains("2027"));
	}

	// --- getSpecificYearCompleteWeekEx ---

	@Test
	void getSpecificYearCompleteWeekEx() {
		String result = DateTimeUtil.getSpecificYearCompleteWeekEx(FIXED_DATE, 2026, 3);
		assertTrue(result.contains("至"));
	}

	@Test
	void getSpecificYearCompleteWeekEx_exceedsYear() {
		String result = DateTimeUtil.getSpecificYearCompleteWeekEx(FIXED_DATE, 2026, 53);
		assertEquals("", result);
	}

	// --- getGeneralYearCompleteWeekEx ---

	@Test
	void getGeneralYearCompleteWeekEx_allYears() {
		for (String yearEx : List.of("今年", "去年", "前年", "明年", "后年")) {
			String result = DateTimeUtil.getGeneralYearCompleteWeekEx(FIXED_DATE, yearEx, 3);
			assertTrue(result.contains("至"), "Failed for yearEx=" + yearEx);
		}
	}

	// --- getSpecificYearMonthLastWeek ---

	@Test
	void getSpecificYearMonthLastWeek() {
		String result = DateTimeUtil.getSpecificYearMonthLastWeek(FIXED_DATE, 2026, 4);
		assertTrue(result.contains("至"));
		assertTrue(result.contains("2026年04月30日"));
	}

	// --- getGeneralMonthLastWeek ---

	@Test
	void getGeneralMonthLastWeek_thisMonth() {
		String result = DateTimeUtil.getGeneralMonthLastWeek(FIXED_DATE, "本月");
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthLastWeek_lastMonth() {
		String result = DateTimeUtil.getGeneralMonthLastWeek(FIXED_DATE, "上月");
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthLastWeek_twoMonthsAgo() {
		String result = DateTimeUtil.getGeneralMonthLastWeek(FIXED_DATE, "上上月");
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthLastWeek_nextMonth() {
		String result = DateTimeUtil.getGeneralMonthLastWeek(FIXED_DATE, "下月");
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthLastWeek_januaryWrap() {
		LocalDate jan = LocalDate.of(2026, 1, 15);
		String result = DateTimeUtil.getGeneralMonthLastWeek(jan, "上月");
		assertTrue(result.contains("2025"));
	}

	// --- getSpecificYearMonthLastCompleteWeekEx ---

	@Test
	void getSpecificYearMonthLastCompleteWeekEx() {
		String result = DateTimeUtil.getSpecificYearMonthLastCompleteWeekEx(FIXED_DATE, 2026, 4);
		assertTrue(result.contains("至"));
	}

	// --- getGeneralMonthLastCompleteWeekEx ---

	@Test
	void getGeneralMonthLastCompleteWeekEx_thisMonth() {
		String result = DateTimeUtil.getGeneralMonthLastCompleteWeekEx(FIXED_DATE, "本月");
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthLastCompleteWeekEx_lastMonth() {
		String result = DateTimeUtil.getGeneralMonthLastCompleteWeekEx(FIXED_DATE, "上月");
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthLastCompleteWeekEx_twoMonthsAgo() {
		String result = DateTimeUtil.getGeneralMonthLastCompleteWeekEx(FIXED_DATE, "上上月");
		assertTrue(result.contains("至"));
	}

	@Test
	void getGeneralMonthLastCompleteWeekEx_nextMonth() {
		String result = DateTimeUtil.getGeneralMonthLastCompleteWeekEx(FIXED_DATE, "下月");
		assertTrue(result.contains("至"));
	}

	// --- getSpecificYearHalfYearEx ---

	@Test
	void getSpecificYearHalfYearEx_firstHalf() {
		String result = DateTimeUtil.getSpecificYearHalfYearEx(FIXED_DATE, 2026, "上");
		assertEquals("2026年01月01日至2026年06月30日", result);
	}

	@Test
	void getSpecificYearHalfYearEx_secondHalf() {
		String result = DateTimeUtil.getSpecificYearHalfYearEx(FIXED_DATE, 2026, "下");
		assertEquals("2026年07月01日至2026年12月31日", result);
	}

	// --- getGeneralYearHalfYearEx ---

	@Test
	void getGeneralYearHalfYearEx_allYears() {
		for (String yearEx : List.of("今年", "去年", "前年", "明年", "后年")) {
			String result = DateTimeUtil.getGeneralYearHalfYearEx(FIXED_DATE, yearEx, "上");
			assertTrue(result.contains("至"), "Failed for yearEx=" + yearEx);
			assertTrue(result.contains("01月01日"), "Failed for yearEx=" + yearEx);
		}
	}

	// --- buildDateExpressions (integration-style) ---

	@Test
	void buildDateExpressions_specificYearMonthDay() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("2026年04月03日"), FIXED_DATE);
		assertEquals(1, result.size());
		assertEquals("2026年04月03日=2026年04月03日", result.get(0));
	}

	@Test
	void buildDateExpressions_generalYearMonthDay() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("今年04月03日"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2026年04月03日"));
	}

	@Test
	void buildDateExpressions_generalMonthDay() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("本月03日"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2026年04月03日"));
	}

	@Test
	void buildDateExpressions_generalDay() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("今天"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2026年04月03日"));
	}

	@Test
	void buildDateExpressions_specificYearMonth() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("2026年04月"), FIXED_DATE);
		assertEquals(1, result.size());
		assertEquals("2026年04月=2026年04月", result.get(0));
	}

	@Test
	void buildDateExpressions_generalYearMonth() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("去年04月"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2025年04月"));
	}

	@Test
	void buildDateExpressions_generalMonth() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("本月"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2026年04月"));
	}

	@Test
	void buildDateExpressions_specificYear() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("2026年"), FIXED_DATE);
		assertEquals(1, result.size());
		assertEquals("2026年=2026年", result.get(0));
	}

	@Test
	void buildDateExpressions_generalYear() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("去年"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2025年"));
	}

	@Test
	void buildDateExpressions_specificYearQuarter() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("2026年第2季度"), FIXED_DATE);
		assertEquals(1, result.size());
		assertEquals("2026年第2季度=2026年第2季度", result.get(0));
	}

	@Test
	void buildDateExpressions_generalYearQuarter() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("今年第2季度"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2026年第2季度"));
	}

	@Test
	void buildDateExpressions_generalQuarter() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("本季度"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2026年第2季度"));
	}

	@Test
	void buildDateExpressions_generalWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("本周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_weekDay() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("本周第3天"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2026年"));
	}

	@Test
	void buildDateExpressions_monthLastDay() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("本月最后一天"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2026年04月30日"));
	}

	@Test
	void buildDateExpressions_yearMonthLastDay() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("今年02月最后一天"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2026年02月28日"));
	}

	@Test
	void buildDateExpressions_generalWeekSpecificDay() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("本周星期3"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("2026年"));
	}

	@Test
	void buildDateExpressions_recentNYear() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("近2年"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_recentNMonth() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("近3个月"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_recentNWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("近2周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_recentNDay() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("近7天"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_recentNDayWithoutToday() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("不包含今天的近7天"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_recentNCompleteYear() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("近2个完整年"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_recentNCompleteQuarter() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("近2个完整季度"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_recentNCompleteMonth() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("近3个完整月"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_recentNCompleteWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("近2个完整周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_recentNQuarterWithCurrent() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("包含当前季度的近2个季度"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_specificYearWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("2026年第05周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_generalYearWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("今年第05周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_generalMonthWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("本月第1周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_specificYearMonthLastWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("2026年04月最后一周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_generalMonthLastWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("本月最后一周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_generalMonthLastCompleteWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("本月最后一个完整周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_specificYearMonthWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("2026年04月第1周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_generalYearMonthWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("今年04月第1周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_specificYearMonthCompleteWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("2026年04月第1个完整周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_generalYearMonthCompleteWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("今年04月第1个完整周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_generalMonthCompleteWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("本月第1个完整周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_specificYearCompleteWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("2026年第05个完整周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_generalYearCompleteWeek() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("今年第05个完整周"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_specificYearHalfYear() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("2026年上半年"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_generalYearHalfYear() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("今年上半年"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_halfYear() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of("上半年"), FIXED_DATE);
		assertEquals(1, result.size());
		assertTrue(result.get(0).contains("至"));
	}

	@Test
	void buildDateExpressions_emptyList() {
		List<String> result = DateTimeUtil.buildDateExpressions(List.of(), FIXED_DATE);
		assertTrue(result.isEmpty());
	}

	// --- buildDateTimeComment ---

	@Test
	void buildDateTimeComment_containsTodayInfo() {
		String result = DateTimeUtil.buildDateTimeComment(List.of("今天"));
		assertTrue(result.contains("今天是"));
		assertTrue(result.contains("需要计算的时间是"));
	}

}
