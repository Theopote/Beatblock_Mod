package com.beatblock.automap.camera;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * One-way creation intent for a camera clip: semantic {@link CameraShot} plus optional live pose.
 * <p>
 * Compiles via {@link CameraShotTimelineWriter} into Timeline. After write, Timeline is the
 * source of truth — this draft is not kept in sync with subsequent keyframe/segment edits.
 */
public record CameraShotDraft(
	CameraShot shot,
	@Nullable CapturedCameraPose pose
) {

	public CameraShotDraft {
		Objects.requireNonNull(shot, "shot");
	}

	/** Framing-resolved draft (Creator Panel / AutoMap). */
	public static CameraShotDraft semantic(CameraShot shot) {
		return new CameraShotDraft(shot, null);
	}

	/** Live-pose draft (Timeline context-menu Add Segment). */
	public static CameraShotDraft poseAnchored(CameraShot shot, CapturedCameraPose pose) {
		return new CameraShotDraft(shot, Objects.requireNonNull(pose, "pose"));
	}

	public boolean isPoseAnchored() {
		return pose != null;
	}

	/**
	 * Context-menu helper: movement + live pose → draft with {@link CameraSubject#worldPosition} subject.
	 */
	public static CameraShotDraft fromLivePose(
		double startSeconds,
		double durationSeconds,
		CameraShotMovement movement,
		CapturedCameraPose pose
	) {
		Objects.requireNonNull(movement, "movement");
		Objects.requireNonNull(pose, "pose");
		CameraSubject subject = lookAtSubject(pose);
		CameraShot shot = new CameraShot(
			startSeconds,
			durationSeconds,
			subject,
			CameraShotFraming.MEDIUM,
			movement,
			null,
			CameraShotTransition.CUT,
			CameraShotEasing.SMOOTH,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.none(),
			-1
		);
		return poseAnchored(shot, pose);
	}

	private static CameraSubject lookAtSubject(CapturedCameraPose pose) {
		if (pose.orbit() != null) {
			CapturedCameraPose.OrbitCapture o = pose.orbit();
			return CameraSubject.worldPosition(o.targetX(), o.targetY(), o.targetZ());
		}
		return CameraSubject.worldPosition(pose.eyeX(), pose.eyeY(), pose.eyeZ());
	}
}
