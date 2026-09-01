package com.beatblock.timeline.playback;

import com.beatblock.automap.camera.CameraValidationRules;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Facade that runs independently composable validation domains. */
public final class TimelineValidator {
	public static final String RULE_DUPLICATE_EVENT_ID = "duplicate_event_id";
	public static final String RULE_INVALID_DURATION = "invalid_duration";
	public static final String RULE_NON_FINITE_EVENT_DURATION = "non_finite_event_duration";
	public static final String RULE_NON_POSITIVE_EVENT_DURATION = "non_positive_event_duration";
	public static final String RULE_MISSING_ANIMATION_PRESET = "missing_animation_preset";
	public static final String RULE_UNSUPPORTED_PAYLOAD = "unsupported_payload";
	public static final String RULE_INVALID_GLOBAL_PAYLOAD = "invalid_global_payload";
	public static final String RULE_UNKNOWN_GLOBAL_EVENT = "unknown_global_event";
	public static final String RULE_INVALID_REFERENCE_BEAT_DATA = "invalid_reference_beat_data";
	public static final String RULE_BPM_METADATA_MISMATCH = "timeline_bpm_metadata_mismatch";
	public static final String RULE_UNBOUND_TARGET = "unbound_target";
	public static final String RULE_MISSING_STAGE_OBJECT = "missing_stage_object";
	public static final String RULE_EVENT_OUTSIDE_TIMELINE = "event_outside_timeline";
	public static final String RULE_MISSING_AUDIO = "missing_audio_asset";
	public static final String RULE_AUDIO_FILE_MISSING = "audio_file_missing";
	public static final String RULE_MISSING_BUILD_LAYER = "missing_build_layer";
	public static final String RULE_MISSING_CAMERA_SUBJECT = CameraValidationRules.MISSING_CAMERA_SUBJECT;
	public static final String RULE_MISSING_CAMERA_LOOK_AT = CameraValidationRules.MISSING_CAMERA_LOOK_AT;
	public static final String RULE_MISSING_CAMERA_BUILD_LAYER = CameraValidationRules.MISSING_CAMERA_BUILD_LAYER;
	public static final String RULE_INVALID_CAMERA_FRAMING = CameraValidationRules.INVALID_CAMERA_FRAMING;
	public static final String RULE_UNSUPPORTED_CAMERA_TRANSITION = CameraValidationRules.UNSUPPORTED_CAMERA_TRANSITION;
	public static final String RULE_COUNT_ANIMATION = "count_animation_events";
	public static final String RULE_COUNT_CAMERA = "count_camera_keyframes";
	public static final String RULE_COUNT_LAYERS = "count_build_layers";
	public static final String RULE_COUNT_MARKERS = "count_markers";

	private static final List<TimelineValidationRule> DEFAULT_RULES = List.of(
		new TimelineStructureValidator(),
		new ReferenceBeatValidator(),
		new CameraTrackValidator(),
		new GlobalTrackValidator(),
		new StageEventValidator(),
		new BuildLayerValidator(),
		new AudioReferenceValidator()
	);

	private TimelineValidator() {}

	public static TimelineValidationReport validate(@Nullable Timeline document) {
		return validate(document, null, null);
	}

	public static TimelineValidationReport validate(
		@Nullable Timeline document,
		@Nullable BlockAnimationEngine engine
	) {
		return validate(document, engine, null);
	}

	public static TimelineValidationReport validate(
		@Nullable Timeline document,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager
	) {
		return validateWithRules(document, engine, layerManager, List.of());
	}

	/** Runs built-in validation followed by caller-provided plugin rules. */
	public static TimelineValidationReport validateWithRules(
		@Nullable Timeline document,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager,
		Iterable<? extends TimelineValidationRule> additionalRules
	) {
		DiagnosticCollector diagnostics = new DiagnosticCollector();
		if (document == null) {
			diagnostics.add(TimelineDiagnostic.error("null_timeline", "Timeline document is null", null, Double.NaN));
			return new TimelineValidationReport(diagnostics.diagnostics(), 0, 0, 0, 0);
		}

		TimelineCompileContext context = TimelineCompileContext.of(document, engine, layerManager);
		List<TimelineValidationRule> rules = new ArrayList<>(DEFAULT_RULES);
		if (additionalRules != null) additionalRules.forEach(rules::add);
		for (TimelineValidationRule rule : rules) {
			if (rule != null) rule.validate(context, diagnostics);
		}
		addSummary(context, diagnostics);
		return new TimelineValidationReport(
			diagnostics.diagnostics(),
			context.animationEventCount(),
			context.cameraKeyframeCount(),
			context.buildLayerCount(),
			context.markerCount()
		);
	}

	private static void addSummary(TimelineCompileContext context, DiagnosticCollector diagnostics) {
		diagnostics.add(TimelineDiagnostic.info(RULE_COUNT_ANIMATION,
			context.animationEventCount() + " animation event(s)"));
		diagnostics.add(TimelineDiagnostic.info(RULE_COUNT_CAMERA,
			context.cameraKeyframeCount() + " camera keyframe(s)"));
		diagnostics.add(TimelineDiagnostic.info(RULE_COUNT_LAYERS,
			context.buildLayerCount() + " build layer(s)"));
		if (context.markerCount() > 0) {
			diagnostics.add(TimelineDiagnostic.info(RULE_COUNT_MARKERS,
				context.markerCount() + " marker(s)"));
		}
	}
}