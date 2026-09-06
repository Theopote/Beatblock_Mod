package com.beatblock.client.camera;

import com.beatblock.automap.camera.CameraSubject;
import org.jspecify.annotations.Nullable;

/**
 * Session-level Camera Creator visualization toggles (not per-clip properties).
 * <p>
 * Toolbar tools for framing work: path / frustum / subject bounds overlays in the world.
 */
public final class CameraCreatorVisualization {

	private static boolean showCameraPath = true;
	private static boolean showFrustum = false;
	private static boolean showSubjectBounds = false;
	private static CameraSubject subjectForBounds = CameraSubject.allStageObjects();

	private CameraCreatorVisualization() {
	}

	public static boolean showCameraPath() {
		return showCameraPath;
	}

	public static void setShowCameraPath(boolean show) {
		showCameraPath = show;
	}

	public static boolean showFrustum() {
		return showFrustum;
	}

	public static void setShowFrustum(boolean show) {
		showFrustum = show;
	}

	public static boolean showSubjectBounds() {
		return showSubjectBounds;
	}

	public static void setShowSubjectBounds(boolean show) {
		showSubjectBounds = show;
	}

	public static CameraSubject subjectForBounds() {
		return subjectForBounds != null ? subjectForBounds : CameraSubject.allStageObjects();
	}

	public static void setSubjectForBounds(@Nullable CameraSubject subject) {
		subjectForBounds = subject != null ? subject : CameraSubject.allStageObjects();
	}

	/** Test / panel reset helper. */
	public static void resetForTests() {
		showCameraPath = true;
		showFrustum = false;
		showSubjectBounds = false;
		subjectForBounds = CameraSubject.allStageObjects();
	}
}
