package com.beatblock.client.camera;

/** Pure ownership contract shared by camera lifecycle tests and the controller. */
public final class CameraOwnershipPolicy {
	private CameraOwnershipPolicy() {}

	public static boolean shouldTakeOwnership(
		boolean timelinePlaying,
		boolean explicitKeyframePreview,
		boolean exporting,
		boolean hasActiveSample
	) {
		return hasActiveSample && (timelinePlaying || explicitKeyframePreview || exporting);
	}
}
