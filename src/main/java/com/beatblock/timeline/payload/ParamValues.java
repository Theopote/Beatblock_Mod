package com.beatblock.timeline.payload;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/** Map 参数安全读取工具（.osc / TimelineEvent parameters 兼容）。 */
public final class ParamValues {

	private ParamValues() {}

	public static @Nullable Object get(@Nullable Map<String, Object> map, String key) {
		return map != null ? map.get(key) : null;
	}

	public static String string(@Nullable Object raw, String fallback) {
		if (raw == null) return fallback != null ? fallback : "";
		String s = String.valueOf(raw).trim();
		return s.isEmpty() && fallback != null ? fallback : s;
	}

	public static String string(@Nullable Map<String, Object> map, String key, String fallback) {
		return string(get(map, key), fallback);
	}

	public static double number(@Nullable Object raw, double fallback) {
		if (raw instanceof Number n) return n.doubleValue();
		if (raw == null) return fallback;
		try {
			return Double.parseDouble(String.valueOf(raw).trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	public static double number(@Nullable Map<String, Object> map, String key, double fallback) {
		return number(get(map, key), fallback);
	}

	public static float floatValue(@Nullable Object raw, float fallback) {
		return (float) number(raw, fallback);
	}

	public static float floatValue(@Nullable Map<String, Object> map, String key, float fallback) {
		return floatValue(get(map, key), fallback);
	}

	public static int intValue(@Nullable Object raw, int fallback) {
		return (int) Math.round(number(raw, fallback));
	}

	public static int intValue(@Nullable Map<String, Object> map, String key, int fallback) {
		return intValue(get(map, key), fallback);
	}

	public static boolean bool(@Nullable Object raw, boolean fallback) {
		if (raw instanceof Boolean b) return b;
		if (raw instanceof Number n) return n.intValue() != 0;
		if (raw == null) return fallback;
		String s = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
		if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
		if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
		return fallback;
	}

	public static boolean bool(@Nullable Map<String, Object> map, String key, boolean fallback) {
		return bool(get(map, key), fallback);
	}

	public static void putIfNonBlank(Map<String, Object> target, String key, @Nullable String value) {
		if (target == null || key == null) return;
		if (value == null || value.isBlank()) return;
		target.put(key, value);
	}
}
