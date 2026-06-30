package com.beatblock.ui.util;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * 编辑器 UI 中数值的显示格式：统一保留有限小数位，避免输入框出现过长浮点串。
 * 内部计算与持久化仍使用完整 double 精度；仅在展示与回填文本框时格式化。
 */
public final class UiNumberFormatter {

	public static final int DEFAULT_DECIMALS = 2;

	private UiNumberFormatter() {}

	public static String format(double value) {
		return format(value, DEFAULT_DECIMALS);
	}

	public static String format(double value, int decimals) {
		int safeDecimals = Math.max(0, decimals);
		return String.format(Locale.ROOT, "%." + safeDecimals + "f", value);
	}

	/** 将事件参数等对象格式化为可编辑文本；非数值类型原样转字符串。 */
	public static String formatParamValue(@Nullable Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof Number number) {
			return format(number.doubleValue());
		}
		return String.valueOf(value);
	}
}
