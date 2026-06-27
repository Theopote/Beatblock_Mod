package com.beatblock.timeline.editing;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.engine.influence.InfluenceDimension;
import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.binding.SpatialDispatchMode;
import com.beatblock.timeline.generation.DistancePacing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 动画事件属性校验与参数 patch 构建（无 ImGui 依赖）。
 */
public final class AnimationEventPropertiesEditor {

	private AnimationEventPropertiesEditor() {}

	public sealed interface Result {
		record Ok(AnimationEventSnapshot snapshot) implements Result {}
		record Err(String message) implements Result {}
	}

	public static Result buildUpdatedSnapshot(
		AnimationEventFormInput input,
		Map<String, Object> existingParameters,
		Predicate<String> targetObjectExists,
		Predicate<String> blockIdValid
	) {
		if (input == null) {
			return new Result.Err("无效表单。");
		}
		if (input.targetObjectId() == null || input.targetObjectId().isBlank()) {
			return new Result.Err("请先选择目标对象。");
		}
		if (!targetObjectExists.test(input.targetObjectId())) {
			return new Result.Err("目标对象不存在，请重新选择。");
		}

		TimelineAnimationActionMode mode = TimelineAnimationActionMode.fromValue(input.actionMode());
		String placeBlockId = null;
		if (mode == TimelineAnimationActionMode.PLACE) {
			String blockId = input.placeBlockId() == null || input.placeBlockId().isBlank()
				? "minecraft:diamond_block"
				: input.placeBlockId().trim();
			if (!blockIdValid.test(blockId)) {
				return new Result.Err("方块ID无效，示例: minecraft:diamond_block");
			}
			placeBlockId = blockId;
		}

		String flashBlockId = null;
		BlockInfluencePreset preset = BlockInfluencePresets.get(input.animationId());
		if (preset != null && !preset.channelsFor(InfluenceDimension.APPEARANCE).isEmpty()) {
			String blockId = input.flashBlockId() == null || input.flashBlockId().isBlank()
				? "minecraft:gold_block"
				: input.flashBlockId().trim();
			if (!blockIdValid.test(blockId)) {
				return new Result.Err("闪烁方块ID无效，示例: minecraft:gold_block");
			}
			flashBlockId = blockId;
		}

		Map<String, Object> parameters = new HashMap<>(
			existingParameters != null ? existingParameters : Map.of()
		);
		TimelineEventOrigin eventOrigin = TimelineEventOrigin.fromValue(parameters.get("eventOrigin"));
		clearManagedParameters(parameters);

		new AnimationEventParams(
			mode,
			input.animationId(),
			input.targetObjectId(),
			input.energy(),
			input.durationSeconds(),
			eventOrigin,
			Map.of()
		).writeCoreInto(parameters);
		parameters.put("energyThreshold", input.energyThreshold());
		parameters.put("dispatchModel", input.stepDispatch() ? "STEP" : "BURST");

		if (input.stepDispatch()) {
			applyStepParameters(parameters, input);
		}

		parameters.put("inheritGroupSpatial", input.inheritGroupSpatial());
		if (input.inheritGroupSpatial()) {
			parameters.remove("spatialMode");
			parameters.remove("sequentialDelaySeconds");
		} else {
			SpatialDispatchMode spatialMode = SpatialDispatchMode.fromValue(input.spatialMode());
			parameters.put("spatialMode", spatialMode.name());
			parameters.put("sequentialDelaySeconds", input.spatialDelaySeconds());
		}

		if (mode == TimelineAnimationActionMode.PLACE) {
			parameters.put("placeBlock", placeBlockId);
		} else {
			parameters.remove("placeBlock");
			parameters.remove("placeBlockId");
		}

		parameters.put("vfxEnabled", input.vfxEnabled());
		if (flashBlockId != null) {
			parameters.put("flashBlock", flashBlockId);
		} else {
			parameters.remove("flashBlock");
			parameters.remove("flashBlockId");
		}

		double clipStart = input.timeSeconds();
		double clipEnd = input.timeSeconds() + input.durationSeconds();
		return new Result.Ok(new AnimationEventSnapshot(
			input.timeSeconds(),
			parameters,
			clipStart,
			clipEnd
		));
	}

	private static void applyStepParameters(Map<String, Object> parameters, AnimationEventFormInput input) {
		parameters.put("pacingMode", input.pacingMode());
		boolean distancePacing = "DISTANCE".equalsIgnoreCase(input.pacingMode());
		if (distancePacing) {
			parameters.put("distancePaceSecondsPerBlock", input.distancePaceSecondsPerBlock());
			parameters.put("distancePaceMinGapSeconds", input.distancePaceMinGapSeconds());
			parameters.remove("blocksPerBeat");
		} else {
			parameters.put("blocksPerBeat", input.blocksPerBeat());
			parameters.remove("distancePaceSecondsPerBlock");
			parameters.remove("distancePaceMinGapSeconds");
		}

		parameters.put("stepStartMode", input.stepStartMode());
		parameters.put("stepCompletionMode", input.stepCompletionMode());
		parameters.put("cameraAdaptiveStep", input.cameraAdaptiveStep());
		parameters.put("cameraFrustumGating", input.cameraFrustumGating());
		parameters.put("cameraEdgePriority", Math.max(0.0, Math.min(1.0, input.cameraEdgePriority())));

		parameters.put("usePhaseAnimation", input.usePhaseAnimation());
		if (input.usePhaseAnimation()) {
			double entry = input.entryDurationPercent();
			double idle = input.idleDurationPercent();
			double exit = input.exitDurationPercent();
			double total = entry + idle + exit;
			if (total > 0.1) {
				entry = (entry / total) * 100.0;
				idle = (idle / total) * 100.0;
				exit = (exit / total) * 100.0;
			}
			parameters.put("entryDurationPercent", entry);
			parameters.put("idleDurationPercent", idle);
			parameters.put("exitDurationPercent", exit);
		} else {
			parameters.remove("entryDurationPercent");
			parameters.remove("idleDurationPercent");
			parameters.remove("exitDurationPercent");
		}

		if (input.cameraAdaptiveStep()) {
			parameters.put("cameraNearDistance", input.cameraNearDistance());
			parameters.put("cameraFarDistance", input.cameraFarDistance());
			parameters.put("cameraNearScale", input.cameraNearScale());
			parameters.put("cameraFarScale", input.cameraFarScale());
		} else {
			parameters.remove("cameraNearDistance");
			parameters.remove("cameraFarDistance");
			parameters.remove("cameraNearScale");
			parameters.remove("cameraFarScale");
		}
	}

	private static void clearManagedParameters(Map<String, Object> parameters) {
		for (String key : MANAGED_PARAMETER_KEYS) {
			parameters.remove(key);
		}
	}

	private static final List<String> MANAGED_PARAMETER_KEYS = List.of(
		"actionMode", "mode", "durationSeconds", "energy", "energyThreshold", "animationType", "targetObject",
		"dispatchModel", "pacingMode", "distancePaceSecondsPerBlock", "distancePaceMinGapSeconds", "blocksPerBeat",
		"stepStartMode", "stepCompletionMode", "cameraAdaptiveStep", "cameraFrustumGating", "cameraEdgePriority",
		"usePhaseAnimation", "entryDurationPercent", "idleDurationPercent", "exitDurationPercent",
		"cameraNearDistance", "cameraFarDistance", "cameraNearScale", "cameraFarScale",
		"inheritGroupSpatial", "spatialMode", "sequentialDelaySeconds",
		"placeBlock", "placeBlockId", "vfxEnabled", "flashBlock", "flashBlockId"
	);

	public static AnimationEventFormInput parseFormInput(
		String timeRaw,
		String durationRaw,
		String energyRaw,
		String energyThresholdRaw,
		String spatialDelayRaw,
		String blocksPerBeatRaw,
		String distancePaceSecondsRaw,
		String distancePaceMinGapRaw,
		String cameraNearDistanceRaw,
		String cameraFarDistanceRaw,
		String cameraNearScaleRaw,
		String cameraFarScaleRaw,
		String cameraEdgePriorityRaw,
		String entryDurationRaw,
		String idleDurationRaw,
		String exitDurationRaw,
		String placeBlockRaw,
		String flashBlockRaw,
		String actionMode,
		String animationId,
		String targetObjectId,
		boolean inheritGroupSpatial,
		String spatialMode,
		boolean stepDispatch,
		String stepStartMode,
		String stepCompletionMode,
		String pacingMode,
		boolean cameraAdaptiveStep,
		boolean cameraFrustumGating,
		boolean usePhaseAnimation,
		boolean vfxEnabled
	) {
		double timeSeconds = Math.max(0.0, Double.parseDouble(trim(timeRaw)));
		double durationSeconds = Math.max(0.01, Double.parseDouble(trim(durationRaw)));
		float energy = (float) Math.max(0.0, Math.min(1.0, Double.parseDouble(trim(energyRaw))));
		float energyThreshold = (float) Math.max(0.0, Math.min(1.0, Double.parseDouble(trim(energyThresholdRaw))));

		double spatialDelay = 0.0;
		if (!inheritGroupSpatial && !trim(spatialDelayRaw).isEmpty()) {
			spatialDelay = Math.max(0.0, Double.parseDouble(trim(spatialDelayRaw)));
		}

		int blocksPerBeat = 1;
		if (stepDispatch && !trim(blocksPerBeatRaw).isEmpty()) {
			blocksPerBeat = Math.max(1, (int) Math.round(Double.parseDouble(trim(blocksPerBeatRaw))));
		}

		double secondsPerBlock = DistancePacing.DEFAULT_SECONDS_PER_BLOCK_UNIT;
		double minGap = DistancePacing.DEFAULT_MIN_GAP_SECONDS;
		if (stepDispatch && "DISTANCE".equalsIgnoreCase(pacingMode)) {
			if (!trim(distancePaceSecondsRaw).isEmpty()) {
				secondsPerBlock = Math.max(0.01, Double.parseDouble(trim(distancePaceSecondsRaw)));
			}
			if (!trim(distancePaceMinGapRaw).isEmpty()) {
				minGap = Math.max(0.0, Double.parseDouble(trim(distancePaceMinGapRaw)));
			}
		}

		double nearDistance = 8.0;
		double farDistance = 48.0;
		double nearScale = 0.6;
		double farScale = 1.5;
		if (stepDispatch && cameraAdaptiveStep) {
			if (!trim(cameraNearDistanceRaw).isEmpty()) {
				nearDistance = Math.max(0.5, Double.parseDouble(trim(cameraNearDistanceRaw)));
			}
			if (!trim(cameraFarDistanceRaw).isEmpty()) {
				farDistance = Math.max(nearDistance + 0.001, Double.parseDouble(trim(cameraFarDistanceRaw)));
			}
			if (!trim(cameraNearScaleRaw).isEmpty()) {
				nearScale = Math.max(0.1, Double.parseDouble(trim(cameraNearScaleRaw)));
			}
			if (!trim(cameraFarScaleRaw).isEmpty()) {
				farScale = Math.max(0.1, Double.parseDouble(trim(cameraFarScaleRaw)));
			}
		}

		double entryPercent = 20.0;
		double idlePercent = 60.0;
		double exitPercent = 20.0;
		if (stepDispatch && usePhaseAnimation) {
			if (!trim(entryDurationRaw).isEmpty()) {
				entryPercent = Math.max(0.0, Math.min(100.0, Double.parseDouble(trim(entryDurationRaw))));
			}
			if (!trim(idleDurationRaw).isEmpty()) {
				idlePercent = Math.max(0.0, Math.min(100.0, Double.parseDouble(trim(idleDurationRaw))));
			}
			if (!trim(exitDurationRaw).isEmpty()) {
				exitPercent = Math.max(0.0, Math.min(100.0, Double.parseDouble(trim(exitDurationRaw))));
			}
		}

		double cameraEdgePriority = 0.0;
		if (!trim(cameraEdgePriorityRaw).isEmpty()) {
			cameraEdgePriority = Double.parseDouble(trim(cameraEdgePriorityRaw));
		}

		return new AnimationEventFormInput(
			timeSeconds,
			durationSeconds,
			energy,
			energyThreshold,
			actionMode,
			animationId,
			targetObjectId,
			inheritGroupSpatial,
			spatialMode,
			spatialDelay,
			stepDispatch,
			stepStartMode,
			stepCompletionMode,
			pacingMode,
			blocksPerBeat,
			secondsPerBlock,
			minGap,
			cameraAdaptiveStep,
			cameraFrustumGating,
			cameraEdgePriority,
			usePhaseAnimation,
			entryPercent,
			idlePercent,
			exitPercent,
			nearDistance,
			farDistance,
			nearScale,
			farScale,
			trim(placeBlockRaw),
			trim(flashBlockRaw),
			vfxEnabled
		);
	}

	private static String trim(String raw) {
		return raw == null ? "" : raw.trim();
	}
}
