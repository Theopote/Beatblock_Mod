package com.beatblock.timeline.editing;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.engine.influence.InfluenceDimension;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.binding.SpatialDispatchMode;
import com.beatblock.timeline.generation.DistancePacing;
import com.beatblock.timeline.payload.DispatchModel;
import com.beatblock.timeline.payload.SingleBlockRef;
import com.beatblock.timeline.payload.SpatialParams;
import com.beatblock.timeline.payload.StageEventPayload;
import com.beatblock.timeline.payload.StageEventPayloadCodec;
import com.beatblock.timeline.payload.StepParams;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 动画事件属性校验与参数 patch 构建（无 ImGui 依赖）。
 * <p>
 * 写回路径经 {@link StageEventPayload} 编码，避免散落字符串键；
 * 未建模字段（mappingProfile、轨迹等）从既有 Map 保留。
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

		// 保留未建模键（mappingProfile、sourceStem、meteor*、layerId 等）
		Map<String, Object> parameters = new HashMap<>(
			existingParameters != null ? existingParameters : Map.of()
		);
		TimelineEventOrigin eventOrigin = TimelineEventOrigin.fromValue(parameters.get("eventOrigin"));
		SingleBlockRef singleBlock = SingleBlockRef.fromMap(parameters).orElse(null);
		String existingLayerId = readLayerId(parameters);
		String existingBuildMode = stringOr(parameters.get("buildMode"), "wall");
		boolean existingDissolve = "true".equalsIgnoreCase(String.valueOf(parameters.get("buildDissolve")));
		String existingBuildPlace = firstNonBlank(
			stringOr(parameters.get("placeBlock"), ""),
			stringOr(parameters.get("placeBlockId"), "")
		);

		clearManagedParameters(parameters);

		StageEventPayload payload = toPayload(
			input,
			mode,
			eventOrigin,
			placeBlockId,
			flashBlockId,
			singleBlock,
			existingLayerId,
			existingBuildMode,
			existingDissolve,
			existingBuildPlace
		);
		parameters.putAll(payload.toParameterMap());
		// layerId 仅 BUILD 载荷建模；其它模式下保留历史字段（与旧 MANAGED 列表行为一致）
		if (mode != TimelineAnimationActionMode.BUILD && existingLayerId != null) {
			parameters.put("layerId", existingLayerId);
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

	/**
	 * 将表单输入编译为强类型载荷（供测试与批量编辑复用）。
	 */
	public static StageEventPayload toPayload(
		AnimationEventFormInput input,
		TimelineAnimationActionMode mode,
		TimelineEventOrigin eventOrigin,
		String placeBlockId,
		String flashBlockId,
		SingleBlockRef singleBlock,
		String layerId,
		String buildMode,
		boolean dissolve,
		String buildPlaceBlockId
	) {
		TimelineEventOrigin origin = eventOrigin != null ? eventOrigin : TimelineEventOrigin.MANUAL;
		String animationType = input.animationId() != null ? input.animationId() : "";
		String target = input.targetObjectId() != null ? input.targetObjectId() : "";
		float energy = input.energy();
		double duration = input.durationSeconds();
		float threshold = input.energyThreshold();

		return switch (mode) {
			case PLACE -> new StageEventPayload.Place(
				animationType,
				target,
				energy,
				duration,
				origin,
				threshold,
				placeBlockId != null ? placeBlockId : "minecraft:diamond_block",
				Map.of()
			);
			case CLEAR -> new StageEventPayload.Clear(
				animationType,
				target,
				energy,
				duration,
				origin,
				threshold,
				Map.of()
			);
			case BUILD -> new StageEventPayload.Build(
				animationType.isBlank() ? "build" : animationType,
				target,
				energy,
				duration,
				origin,
				threshold,
				buildMode != null && !buildMode.isBlank() ? buildMode : "wall",
				dissolve,
				blankToNull(buildPlaceBlockId),
				blankToNull(layerId),
				Map.of()
			);
			case ANIMATE -> new StageEventPayload.Animate(
				animationType,
				target,
				energy,
				duration,
				origin,
				threshold,
				input.stepDispatch() ? DispatchModel.STEP : DispatchModel.BURST,
				spatialFromInput(input),
				stepFromInput(input),
				blankToNull(flashBlockId),
				input.vfxEnabled(),
				singleBlock,
				Map.of()
			);
		};
	}

	private static SpatialParams spatialFromInput(AnimationEventFormInput input) {
		boolean inherit = input.inheritGroupSpatial();
		SpatialDispatchMode mode = SpatialDispatchMode.fromValue(input.spatialMode());
		double delay = inherit ? -1.0 : Math.max(0.0, input.spatialDelaySeconds());
		return new SpatialParams(inherit, mode, delay);
	}

	private static StepParams stepFromInput(AnimationEventFormInput input) {
		if (!input.stepDispatch()) {
			return StepParams.DEFAULT;
		}
		double entry = input.entryDurationPercent();
		double idle = input.idleDurationPercent();
		double exit = input.exitDurationPercent();
		if (input.usePhaseAnimation()) {
			double total = entry + idle + exit;
			if (total > 0.1) {
				entry = (entry / total) * 100.0;
				idle = (idle / total) * 100.0;
				exit = (exit / total) * 100.0;
			}
		}
		return new StepParams(
			input.stepStartMode() != null ? input.stepStartMode() : "NEXT_BEAT",
			input.stepCompletionMode() != null ? input.stepCompletionMode() : "KEEP",
			input.pacingMode() != null ? input.pacingMode() : "BEAT_GRID",
			Math.max(1, input.blocksPerBeat()),
			input.distancePaceSecondsPerBlock(),
			input.distancePaceMinGapSeconds(),
			input.cameraAdaptiveStep(),
			input.cameraFrustumGating(),
			input.cameraEdgePriority(),
			input.usePhaseAnimation(),
			entry,
			idle,
			exit,
			input.cameraNearDistance(),
			input.cameraFarDistance(),
			input.cameraNearScale(),
			input.cameraFarScale()
		);
	}

	private static void clearManagedParameters(Map<String, Object> parameters) {
		for (String key : StageEventPayloadCodec.KNOWN_KEYS) {
			parameters.remove(key);
		}
	}

	private static String readLayerId(Map<String, Object> parameters) {
		Object raw = parameters.get("layerId");
		if (raw == null) return null;
		String id = String.valueOf(raw).trim();
		return id.isEmpty() ? null : id;
	}

	private static String stringOr(Object raw, String fallback) {
		if (raw == null) return fallback;
		String s = String.valueOf(raw).trim();
		return s.isEmpty() ? fallback : s;
	}

	private static String firstNonBlank(String a, String b) {
		if (a != null && !a.isBlank()) return a;
		if (b != null && !b.isBlank()) return b;
		return "";
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}

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
