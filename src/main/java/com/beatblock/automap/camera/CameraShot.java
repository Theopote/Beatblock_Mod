package com.beatblock.automap.camera;

import com.beatblock.automap.engine.CameraAction;
import org.jspecify.annotations.Nullable;

/**
 * 完整镜头语义：时间、主体、取景、运动、过渡与节拍对齐。
 */
public record CameraShot(
	double startSeconds,
	double durationSeconds,
	CameraSubject subject,
	CameraShotFraming framing,
	CameraShotMovement movement,
	@Nullable CameraSubject lookAt,
	CameraShotTransition transition,
	CameraShotEasing easing,
	CameraCollisionPolicy collisionPolicy,
	CameraShotBeatAlignment beatAlignment,
	int sectionIndex
) {

	public CameraShot {
		startSeconds = Math.max(0, startSeconds);
		durationSeconds = Math.max(0.05, durationSeconds);
		subject = subject != null ? subject : CameraSubject.allStageObjects();
		framing = framing != null ? framing : CameraShotFraming.MEDIUM;
		movement = movement != null ? movement : CameraShotMovement.HOLD;
		transition = transition != null ? transition : CameraShotTransition.CUT;
		easing = easing != null ? easing : CameraShotEasing.SMOOTH;
		collisionPolicy = collisionPolicy != null ? collisionPolicy : CameraCollisionPolicy.AVOID_BLOCKS;
		beatAlignment = beatAlignment != null ? beatAlignment : CameraShotBeatAlignment.none();
		sectionIndex = Math.max(-1, sectionIndex);
	}

	public double endSeconds() {
		return startSeconds + durationSeconds;
	}

	public CameraAction legacyAction() {
		return movement.toLegacyAction();
	}

	public String summary() {
		return movement.name() + "(" + subject.displayLabel() + ")";
	}

	public CameraSubject effectiveLookAt() {
		return lookAt != null ? lookAt : subject;
	}
}
