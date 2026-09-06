package com.beatblock.timeline;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Marker 语义入口：结构保护委托 {@link MarkerEditPolicy}；SECTION 查找供 Animation Binding 使用。
 * <p>
 * SECTION Marker 是 Music Structure 的投影，不是运行时执行单元。
 * 不要用名字 {@code "SECTION ..."} 去猜 {@link MarkerOrigin}。
 */
public final class MarkerSemanticService {

	private MarkerSemanticService() {
	}

	/** Binding / section filter：返回 {@code timeSeconds} 处生效的规范化段落标签（小写）。 */
	public static String sectionLabelAtTime(@Nullable Timeline timeline, double timeSeconds) {
		if (timeline == null) {
			return "";
		}
		String current = "";
		for (TimelineMarker marker : timeline.getMarkers()) {
			if (marker == null || !marker.getType().isStructural()) {
				continue;
			}
			if (marker.getTimeSeconds() > timeSeconds) {
				break;
			}
			String label = normalizeSectionLabel(extractSectionLabel(marker.getName()));
			if (!label.isBlank()) {
				current = label;
			}
		}
		return current;
	}

	/**
	 * 去掉常见 {@code SECTION } 前缀后的显示标签（保留大小写，供 Plan 投影等使用）。
	 * 不是 origin 推断依据。
	 */
	public static String extractSectionLabel(@Nullable String markerName) {
		if (markerName == null) {
			return "";
		}
		String raw = markerName.trim();
		if (raw.isBlank()) {
			return "";
		}
		String upper = raw.toUpperCase(Locale.ROOT);
		if (upper.startsWith("SECTION ")) {
			raw = raw.substring("SECTION ".length()).trim();
		}
		return raw;
	}

	/** Binding 比较用：trim + lower-case。 */
	public static String normalizeSectionLabel(@Nullable String value) {
		if (value == null) {
			return "";
		}
		String s = value.trim().toLowerCase(Locale.ROOT);
		return s.isBlank() ? "" : s;
	}

	public static boolean isStructural(@Nullable TimelineMarker marker) {
		return marker != null && marker.getType().isStructural();
	}

	public static boolean requiresStructuralConfirm(
		@Nullable TimelineMarker marker,
		MarkerEditPolicy.StructuralAction action,
		@Nullable MarkerType newType
	) {
		return MarkerEditPolicy.requiresStructuralConfirm(marker, action, newType);
	}

	public static boolean allowsMutation(
		@Nullable TimelineMarker marker,
		MarkerEditPolicy.StructuralAction action,
		@Nullable MarkerType newType,
		boolean structuralConfirmed
	) {
		return MarkerEditPolicy.allowsMutation(marker, action, newType, structuralConfirmed);
	}

	public static boolean isReplaceableByAudioAnalysis(@Nullable TimelineMarker marker) {
		return MarkerEditPolicy.isReplaceableByAudioAnalysis(marker);
	}
}
