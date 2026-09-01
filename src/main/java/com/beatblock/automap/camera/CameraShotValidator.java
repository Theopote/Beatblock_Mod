package com.beatblock.automap.camera;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.playback.TimelineDiagnostic;
import com.beatblock.timeline.playback.TimelineDiagnosticSeverity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 校验 {@link CameraShot} 与 Timeline 摄像机片段语义，避免静默退化到世界原点。 */
public final class CameraShotValidator {

	private CameraShotValidator() {}

	public static List<TimelineDiagnostic> validate(CameraShot shot) {
		return validate(shot, null, null);
	}

	public static List<TimelineDiagnostic> validate(
		CameraShot shot,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager
	) {
		List<TimelineDiagnostic> diagnostics = new ArrayList<>();
		if (shot == null) return diagnostics;
		validateTransition(shot.transition().name(), diagnostics, shot.startSeconds(), null);
		validateSubject(shot.subject(), CameraSubjectRole.SUBJECT, diagnostics, shot.startSeconds(), null, engine, layerManager);
		CameraSubject lookAt = shot.lookAt();
		if (lookAt != null) {
			validateSubject(lookAt, CameraSubjectRole.LOOK_AT, diagnostics, shot.startSeconds(), null, engine, layerManager);
		} else {
			validateSubject(shot.subject(), CameraSubjectRole.LOOK_AT, diagnostics, shot.startSeconds(), null, engine, layerManager);
		}
		return diagnostics;
	}

	public static List<TimelineDiagnostic> validateSegment(
		Map<String, Object> segmentParams,
		double clipStartSeconds,
		@Nullable String segmentEventId,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager
	) {
		List<TimelineDiagnostic> diagnostics = new ArrayList<>();
		if (segmentParams == null || segmentParams.isEmpty()) return diagnostics;

		String transition = stringParam(segmentParams, CameraSegmentSemantics.KEY_TRANSITION);
		if (!transition.isBlank()) {
			validateTransition(transition, diagnostics, clipStartSeconds, segmentEventId);
		}
		String framing = stringParam(segmentParams, "framing");
		if (!framing.isBlank()) {
			validateFramingName(framing, diagnostics, clipStartSeconds, segmentEventId);
		}
		CameraSubject follow = CameraSegmentSemantics.followSubjectFrom(segmentParams);
		if (follow != null) {
			validateSubject(follow, CameraSubjectRole.LOOK_AT, diagnostics, clipStartSeconds, segmentEventId, engine, layerManager);
		}
		return diagnostics;
	}

	public static boolean hasErrors(List<TimelineDiagnostic> diagnostics) {
		return diagnostics.stream().anyMatch(d -> d.severity() == TimelineDiagnosticSeverity.ERROR);
	}

	static void validateSubject(
		CameraSubject subject,
		CameraSubjectRole role,
		List<TimelineDiagnostic> diagnostics,
		double timeSeconds,
		@Nullable String eventId,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager
	) {
		CameraSubjectResolveResult result = CameraSubjectResolver.resolveResult(subject, role, engine, layerManager);
		if (result.resolved()) return;
		String ruleId = result.ruleId();
		if (ruleId == null || ruleId.isBlank()) {
			ruleId = CameraValidationRules.MISSING_CAMERA_SUBJECT;
		}
		String detail = result.detail();
		if (detail == null || detail.isBlank()) {
			detail = "Camera subject could not be resolved";
		}
		diagnostics.add(TimelineDiagnostic.error(ruleId, detail, eventId, timeSeconds));
	}

	private static void validateFramingName(String framing, List<TimelineDiagnostic> diagnostics,
		double timeSeconds, @Nullable String eventId) {
		try {
			CameraShotFraming.valueOf(framing.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			diagnostics.add(TimelineDiagnostic.error(
				CameraValidationRules.INVALID_CAMERA_FRAMING,
				"Unknown camera framing: " + framing,
				eventId,
				timeSeconds
			));
		}
	}

	private static void validateTransition(String transition, List<TimelineDiagnostic> diagnostics,
		double timeSeconds, @Nullable String eventId) {
		String normalized = transition.trim().toUpperCase(Locale.ROOT);
		try {
			CameraShotTransition.valueOf(normalized);
		} catch (IllegalArgumentException ex) {
			diagnostics.add(TimelineDiagnostic.error(
				CameraValidationRules.UNSUPPORTED_CAMERA_TRANSITION,
				"Unsupported camera transition: " + transition,
				eventId,
				timeSeconds
			));
		}
	}

	private static String stringParam(Map<String, Object> params, String key) {
		Object raw = params.get(key);
		return raw != null ? String.valueOf(raw).trim() : "";
	}
}
