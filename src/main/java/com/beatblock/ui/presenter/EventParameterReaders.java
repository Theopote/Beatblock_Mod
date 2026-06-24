package com.beatblock.ui.presenter;

import java.util.Map;

/** 从 Timeline 事件参数字典读取 typed 值（无 ImGui 依赖）。 */
public final class EventParameterReaders {

	private EventParameterReaders() {}

	public static String stringParam(Map<String, Object> params, String key) {
		return stringParam(params, key, "");
	}

	public static String stringParam(Map<String, Object> params, String key, String fallback) {
		Object value = params != null ? params.get(key) : null;
		return value != null ? String.valueOf(value) : fallback;
	}

	public static double numericParam(Map<String, Object> params, String key, double fallback) {
		Object value = params != null ? params.get(key) : null;
		return value instanceof Number number ? number.doubleValue() : fallback;
	}

	public static boolean booleanParam(Map<String, Object> params, String key, boolean fallback) {
		Object value = params != null ? params.get(key) : null;
		if (value instanceof Boolean bool) {
			return bool;
		}
		if (value instanceof Number number) {
			return number.intValue() != 0;
		}
		if (value == null) {
			return fallback;
		}
		String s = String.valueOf(value).trim();
		if ("true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s)) {
			return true;
		}
		if ("false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s)) {
			return false;
		}
		return fallback;
	}
}
