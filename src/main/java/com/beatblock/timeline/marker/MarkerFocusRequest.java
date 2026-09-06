package com.beatblock.timeline.marker;

import org.jspecify.annotations.Nullable;

/**
 * Cross-panel request: after creating a marker, focus its name field for immediate rename.
 */
public final class MarkerFocusRequest {

	private static volatile @Nullable String pendingMarkerId;
	private static volatile boolean focusName;

	private MarkerFocusRequest() {
	}

	public static void requestRename(@Nullable String markerId) {
		if (markerId == null || markerId.isBlank()) {
			clear();
			return;
		}
		pendingMarkerId = markerId;
		focusName = true;
	}

	public static @Nullable String peekMarkerId() {
		return pendingMarkerId;
	}

	public static boolean consumeFocusName() {
		if (!focusName) {
			return false;
		}
		focusName = false;
		return true;
	}

	public static void clear() {
		pendingMarkerId = null;
		focusName = false;
	}
}
