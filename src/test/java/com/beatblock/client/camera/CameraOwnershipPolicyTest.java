package com.beatblock.client.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraOwnershipPolicyTest {
	@Test
	void loadedCameraTrackDoesNotTakeOwnershipWhilePaused() {
		assertFalse(CameraOwnershipPolicy.shouldTakeOwnership(false, false, false, true));
	}

	@Test
	void scrubbingIsNotAnOwnershipReason() {
		// There is intentionally no scrubbing input in the ownership contract.
		assertFalse(CameraOwnershipPolicy.shouldTakeOwnership(false, false, false, true));
	}

	@Test
	void playbackPreviewAndExportRequireAnActualSample() {
		assertTrue(CameraOwnershipPolicy.shouldTakeOwnership(true, false, false, true));
		assertTrue(CameraOwnershipPolicy.shouldTakeOwnership(false, true, false, true));
		assertTrue(CameraOwnershipPolicy.shouldTakeOwnership(false, false, true, true));
		assertFalse(CameraOwnershipPolicy.shouldTakeOwnership(true, false, false, false));
	}
}
