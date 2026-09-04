package com.beatblock.automap.camera;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.TimingSnapDefaults;
import com.beatblock.automap.engine.CameraAction;

/**
 * {@link CameraShot} 与 {@link ChoreographyPlan.CameraPhrase} 互转。
 */
public final class CameraShotCodec {

	private CameraShotCodec() {}

	public static ChoreographyPlan.CameraPhrase toPhrase(CameraShot shot) {
		return new ChoreographyPlan.CameraPhrase(
			shot.startSeconds(),
			shot.summary(),
			shot.sectionIndex(),
			shot.subject().kind().name(),
			shot.subject().refId(),
			shot.durationSeconds(),
			shot.framing().name(),
			shot.movement().name(),
			shot.easing().name(),
			shot.beatAlignment().snapStartToBeat(),
			TimingSnapDefaults.forCameraShot(shot),
			shot.transition().name()
		);
	}

	public static CameraShot fromPhrase(ChoreographyPlan.CameraPhrase phrase) {
		if (phrase == null) {
			return legacyShot(0, CameraAction.HOLD, -1);
		}
		CameraSubject subject = decodeSubject(phrase.subjectKind(), phrase.subjectRef());
		CameraShotFraming framing = parseEnum(phrase.framing(), CameraShotFraming.class, CameraShotFraming.MEDIUM);
		CameraShotMovement movement = phrase.movement().isBlank()
			? CameraShotMovement.fromLegacyAction(parseLegacyAction(phrase.action()))
			: parseEnum(phrase.movement(), CameraShotMovement.class, CameraShotMovement.HOLD);
		CameraShotEasing easing = parseEnum(phrase.easing(), CameraShotEasing.class, CameraShotEasing.SMOOTH);
		CameraShotTransition transition = CameraShotTransition.parse(phrase.transition(), CameraShotTransition.CUT);
		return new CameraShot(
			phrase.timeSeconds(),
			phrase.durationSeconds(),
			subject,
			framing,
			movement,
			subject,
			transition,
			easing,
			CameraCollisionPolicy.AVOID_BLOCKS,
			phrase.beatAligned() ? CameraShotBeatAlignment.onBeat() : CameraShotBeatAlignment.none(),
			phrase.sectionIndex()
		);
	}

	public static CameraShot legacyShot(double timeSeconds, CameraAction action, int sectionIndex) {
		CameraShotMovement movement = CameraShotMovement.fromLegacyAction(action);
		return new CameraShot(
			timeSeconds,
			3.0,
			CameraSubject.allStageObjects(),
			CameraShotFraming.MEDIUM,
			movement,
			CameraSubject.allStageObjects(),
			CameraShotTransition.CUT,
			CameraShotEasing.SMOOTH,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.none(),
			sectionIndex
		);
	}

	private static CameraSubject decodeSubject(String kindName, String refId) {
		if (kindName == null || kindName.isBlank()) {
			return CameraSubject.allStageObjects();
		}
		try {
			return switch (CameraSubjectKind.valueOf(kindName)) {
				case STAGE_OBJECT -> CameraSubject.stageObject(refId);
				case STAGE_GROUP -> CameraSubject.stageGroup(refId);
				case BUILD_LAYER -> CameraSubject.buildLayer(refId);
				case ANIMATED_TARGET -> CameraSubject.animatedTarget(refId);
				case WORLD_POSITION -> CameraSubject.worldPosition(0, 64, 0);
				case ALL_STAGE_OBJECTS -> CameraSubject.allStageObjects();
			};
		} catch (IllegalArgumentException ignored) {
			return CameraSubject.allStageObjects();
		}
	}

	private static CameraAction parseLegacyAction(String action) {
		if (action == null || action.isBlank()) return CameraAction.HOLD;
		try {
			return CameraAction.valueOf(action);
		} catch (IllegalArgumentException ignored) {
			return CameraAction.HOLD;
		}
	}

	private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E fallback) {
		if (raw == null || raw.isBlank()) return fallback;
		try {
			return Enum.valueOf(type, raw);
		} catch (IllegalArgumentException ignored) {
			return fallback;
		}
	}
}
