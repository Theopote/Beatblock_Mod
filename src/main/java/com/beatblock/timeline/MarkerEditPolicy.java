package com.beatblock.timeline;

import org.jspecify.annotations.Nullable;

/**
 * SECTION / 系统 Marker 的保护策略：不禁止一切编辑，但区分可静默改与需确认的结构变更。
 */
public final class MarkerEditPolicy {

	public enum StructuralAction {
		CHANGE_TYPE,
		DELETE
	}

	private MarkerEditPolicy() {
	}

	public static boolean isLocked(@Nullable TimelineMarker marker) {
		return marker != null && marker.getEditState().isLocked();
	}

	/** 名称/时间等非结构字段：锁定则禁止，其它允许（GENERATED 会升为 USER_EDITED）。 */
	public static boolean allowsContentEdit(@Nullable TimelineMarker marker) {
		return marker != null && !isLocked(marker);
	}

	/**
	 * 类型切换或删除是否需要二次确认（SECTION 或系统生成结构）。
	 */
	public static boolean requiresStructuralConfirm(
		@Nullable TimelineMarker marker,
		StructuralAction action,
		@Nullable MarkerType newType
	) {
		if (marker == null || action == null || isLocked(marker)) {
			return false;
		}
		if (action == StructuralAction.CHANGE_TYPE) {
			if (newType == null || newType == marker.getType()) {
				return false;
			}
			return isStructuralSection(marker) || marker.getEditState() == MarkerEditState.GENERATED;
		}
		// DELETE
		return isStructuralSection(marker) || marker.getEditState() == MarkerEditState.GENERATED;
	}

	public static boolean allowsMutation(
		@Nullable TimelineMarker marker,
		StructuralAction action,
		@Nullable MarkerType newType,
		boolean structuralConfirmed
	) {
		if (marker == null) {
			return false;
		}
		if (isLocked(marker)) {
			return false;
		}
		if (requiresStructuralConfirm(marker, action, newType)) {
			return structuralConfirmed;
		}
		return true;
	}

	/** Audio Analysis 重跑时可静默替换的 SECTION。 */
	public static boolean isReplaceableByAudioAnalysis(@Nullable TimelineMarker marker) {
		if (marker == null || marker.getType() != MarkerType.SECTION) {
			return false;
		}
		if (!marker.getEditState().isReplaceableByGeneration()) {
			return false;
		}
		return marker.getOrigin().isSystemProduced() || marker.getOrigin() == MarkerOrigin.AUDIO_ANALYSIS;
	}

	private static boolean isStructuralSection(TimelineMarker marker) {
		return marker.getType() == MarkerType.SECTION;
	}
}
