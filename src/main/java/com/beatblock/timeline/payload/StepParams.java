package com.beatblock.timeline.payload;

import com.beatblock.timeline.generation.DistancePacing;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/** STEP 派发专用参数（dispatchModel=STEP 时有效）。 */
public record StepParams(
	@NonNull String stepStartMode,
	@NonNull String stepCompletionMode,
	@NonNull String pacingMode,
	int blocksPerBeat,
	double distancePaceSecondsPerBlock,
	double distancePaceMinGapSeconds,
	boolean cameraAdaptiveStep,
	boolean cameraFrustumGating,
	double cameraEdgePriority,
	boolean usePhaseAnimation,
	double entryDurationPercent,
	double idleDurationPercent,
	double exitDurationPercent,
	double cameraNearDistance,
	double cameraFarDistance,
	double cameraNearScale,
	double cameraFarScale
) {

	public static final StepParams DEFAULT = new StepParams(
		"NEXT_BEAT",
		"KEEP",
		"BEAT_GRID",
		1,
		DistancePacing.DEFAULT_SECONDS_PER_BLOCK_UNIT,
		DistancePacing.DEFAULT_MIN_GAP_SECONDS,
		false,
		false,
		0.0,
		false,
		20.0,
		60.0,
		20.0,
		8.0,
		48.0,
		0.6,
		1.5
	);

	public StepParams {
		stepStartMode = stepStartMode != null && !stepStartMode.isBlank() ? stepStartMode : "NEXT_BEAT";
		stepCompletionMode = stepCompletionMode != null && !stepCompletionMode.isBlank()
			? stepCompletionMode : "KEEP";
		pacingMode = pacingMode != null && !pacingMode.isBlank() ? pacingMode : "BEAT_GRID";
		blocksPerBeat = Math.max(1, blocksPerBeat);
		distancePaceSecondsPerBlock = Math.max(0.01, distancePaceSecondsPerBlock);
		distancePaceMinGapSeconds = Math.max(0.0, distancePaceMinGapSeconds);
		cameraEdgePriority = Math.max(0.0, Math.min(1.0, cameraEdgePriority));
	}

	public boolean isDistancePacing() {
		return "DISTANCE".equalsIgnoreCase(pacingMode);
	}

	public static @NonNull StepParams fromMap(@Nullable Map<String, Object> map) {
		if (map == null || map.isEmpty()) return DEFAULT;
		return new StepParams(
			ParamValues.string(map, "stepStartMode", DEFAULT.stepStartMode),
			ParamValues.string(map, "stepCompletionMode", DEFAULT.stepCompletionMode),
			ParamValues.string(map, "pacingMode", DEFAULT.pacingMode),
			Math.max(1, ParamValues.intValue(map, "blocksPerBeat", DEFAULT.blocksPerBeat)),
			ParamValues.number(map, "distancePaceSecondsPerBlock", DEFAULT.distancePaceSecondsPerBlock),
			ParamValues.number(map, "distancePaceMinGapSeconds", DEFAULT.distancePaceMinGapSeconds),
			ParamValues.bool(map, "cameraAdaptiveStep", false),
			ParamValues.bool(map, "cameraFrustumGating", false),
			ParamValues.number(map, "cameraEdgePriority", 0.0),
			ParamValues.bool(map, "usePhaseAnimation", false),
			ParamValues.number(map, "entryDurationPercent", DEFAULT.entryDurationPercent),
			ParamValues.number(map, "idleDurationPercent", DEFAULT.idleDurationPercent),
			ParamValues.number(map, "exitDurationPercent", DEFAULT.exitDurationPercent),
			ParamValues.number(map, "cameraNearDistance", DEFAULT.cameraNearDistance),
			ParamValues.number(map, "cameraFarDistance", DEFAULT.cameraFarDistance),
			ParamValues.number(map, "cameraNearScale", DEFAULT.cameraNearScale),
			ParamValues.number(map, "cameraFarScale", DEFAULT.cameraFarScale)
		);
	}

	public void writeInto(@NonNull Map<String, Object> target) {
		target.put("pacingMode", pacingMode);
		if (isDistancePacing()) {
			target.put("distancePaceSecondsPerBlock", distancePaceSecondsPerBlock);
			target.put("distancePaceMinGapSeconds", distancePaceMinGapSeconds);
		} else {
			target.put("blocksPerBeat", blocksPerBeat);
		}
		target.put("stepStartMode", stepStartMode);
		target.put("stepCompletionMode", stepCompletionMode);
		target.put("cameraAdaptiveStep", cameraAdaptiveStep);
		target.put("cameraFrustumGating", cameraFrustumGating);
		target.put("cameraEdgePriority", cameraEdgePriority);
		target.put("usePhaseAnimation", usePhaseAnimation);
		if (usePhaseAnimation) {
			target.put("entryDurationPercent", entryDurationPercent);
			target.put("idleDurationPercent", idleDurationPercent);
			target.put("exitDurationPercent", exitDurationPercent);
		}
		if (cameraAdaptiveStep) {
			target.put("cameraNearDistance", cameraNearDistance);
			target.put("cameraFarDistance", cameraFarDistance);
			target.put("cameraNearScale", cameraNearScale);
			target.put("cameraFarScale", cameraFarScale);
		}
	}

	/** 规范化 pacing 枚举名（写回 map 时用大写）。 */
	public @NonNull String pacingModeNormalized() {
		return pacingMode.trim().toUpperCase(Locale.ROOT);
	}
}
