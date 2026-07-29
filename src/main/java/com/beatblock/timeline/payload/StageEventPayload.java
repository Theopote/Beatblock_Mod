package com.beatblock.timeline.payload;

import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEventOrigin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * 舞台事件强类型载荷（按 {@link TimelineAnimationActionMode} 分派）。
 * <p>
 * 持久化仍通过 {@link #toParameterMap()} 写回与历史兼容的 {@code Map} 键名；
 * 未知键进入 {@link #extensions()}，不丢失数据。
 */
public sealed interface StageEventPayload
	permits StageEventPayload.Animate, StageEventPayload.Build, StageEventPayload.Place, StageEventPayload.Clear {

	@NonNull TimelineAnimationActionMode actionMode();

	@NonNull String animationType();

	@NonNull String targetObject();

	float energy();

	double durationSeconds();

	@NonNull TimelineEventOrigin eventOrigin();

	/** 播放门槛：energy &lt; threshold 时跳过。缺省 0。 */
	float energyThreshold();

	/** 未建模的扩展键（binding 元数据、自定义字段等）。 */
	@NonNull Map<String, Object> extensions();

	/** 序列化为 TimelineEvent / .osc 参数字典。 */
	@NonNull Map<String, Object> toParameterMap();

	default boolean passesEnergyGate() {
		return energy() + 1e-6f >= Math.max(0f, Math.min(1f, energyThreshold()));
	}

	default boolean isStepDispatch() {
		return this instanceof Animate a && a.dispatchModel() == DispatchModel.STEP;
	}

	/** 解析 PLACE/BUILD 的放置方块 ID（若有）。 */
	default @NonNull Optional<String> resolvePlaceBlockId() {
		if (this instanceof Place p) return Optional.of(p.placeBlockId()).filter(s -> !s.isBlank());
		if (this instanceof Build b) return Optional.ofNullable(b.placeBlockId()).filter(s -> !s.isBlank());
		return Optional.empty();
	}

	// ── 变体 ──────────────────────────────────────────────────────────

	/**
	 * 渲染层动画（ANIMATE）：preset + 派发/空间/STEP + 可选单块烘焙坐标。
	 */
	record Animate(
		@NonNull String animationType,
		@NonNull String targetObject,
		float energy,
		double durationSeconds,
		@NonNull TimelineEventOrigin eventOrigin,
		float energyThreshold,
		@NonNull DispatchModel dispatchModel,
		@NonNull SpatialParams spatial,
		@NonNull StepParams step,
		@Nullable String flashBlockId,
		boolean vfxEnabled,
		@Nullable SingleBlockRef singleBlock,
		@NonNull Map<String, Object> extensions
	) implements StageEventPayload {

		public Animate {
			animationType = animationType != null ? animationType : "";
			targetObject = targetObject != null ? targetObject : "";
			energy = clamp01(energy);
			durationSeconds = Math.max(0.01, durationSeconds);
			eventOrigin = eventOrigin != null ? eventOrigin : TimelineEventOrigin.MANUAL;
			energyThreshold = clamp01(energyThreshold);
			dispatchModel = dispatchModel != null ? dispatchModel : DispatchModel.BURST;
			spatial = spatial != null ? spatial : SpatialParams.DEFAULT;
			step = step != null ? step : StepParams.DEFAULT;
			if (flashBlockId != null && flashBlockId.isBlank()) flashBlockId = null;
			extensions = extensions != null ? Map.copyOf(extensions) : Map.of();
		}

		@Override
		public @NonNull TimelineAnimationActionMode actionMode() {
			return TimelineAnimationActionMode.ANIMATE;
		}

		@Override
		public @NonNull Map<String, Object> toParameterMap() {
			return StageEventPayloadCodec.encode(this);
		}
	}

	/**
	 * 建造序列（BUILD）：按序出现/溶解，可绑定图层。
	 */
	record Build(
		@NonNull String animationType,
		@NonNull String targetObject,
		float energy,
		double durationSeconds,
		@NonNull TimelineEventOrigin eventOrigin,
		float energyThreshold,
		@NonNull String buildMode,
		boolean dissolve,
		@Nullable String placeBlockId,
		@Nullable String layerId,
		@NonNull Map<String, Object> extensions
	) implements StageEventPayload {

		public Build {
			animationType = animationType != null ? animationType : "";
			targetObject = targetObject != null ? targetObject : "";
			energy = clamp01(energy);
			durationSeconds = Math.max(0.01, durationSeconds);
			eventOrigin = eventOrigin != null ? eventOrigin : TimelineEventOrigin.MANUAL;
			energyThreshold = clamp01(energyThreshold);
			buildMode = buildMode != null && !buildMode.isBlank() ? buildMode : "wall";
			if (placeBlockId != null && placeBlockId.isBlank()) placeBlockId = null;
			if (layerId != null && layerId.isBlank()) layerId = null;
			extensions = extensions != null ? Map.copyOf(extensions) : Map.of();
		}

		@Override
		public @NonNull TimelineAnimationActionMode actionMode() {
			return TimelineAnimationActionMode.BUILD;
		}

		@Override
		public @NonNull Map<String, Object> toParameterMap() {
			return StageEventPayloadCodec.encode(this);
		}
	}

	/**
	 * 瞬间放置方块（PLACE）。
	 */
	record Place(
		@NonNull String animationType,
		@NonNull String targetObject,
		float energy,
		double durationSeconds,
		@NonNull TimelineEventOrigin eventOrigin,
		float energyThreshold,
		@NonNull String placeBlockId,
		@NonNull Map<String, Object> extensions
	) implements StageEventPayload {

		public Place {
			animationType = animationType != null ? animationType : "";
			targetObject = targetObject != null ? targetObject : "";
			energy = clamp01(energy);
			durationSeconds = Math.max(0.01, durationSeconds);
			eventOrigin = eventOrigin != null ? eventOrigin : TimelineEventOrigin.MANUAL;
			energyThreshold = clamp01(energyThreshold);
			placeBlockId = placeBlockId != null && !placeBlockId.isBlank()
				? placeBlockId : "minecraft:diamond_block";
			extensions = extensions != null ? Map.copyOf(extensions) : Map.of();
		}

		@Override
		public @NonNull TimelineAnimationActionMode actionMode() {
			return TimelineAnimationActionMode.PLACE;
		}

		@Override
		public @NonNull Map<String, Object> toParameterMap() {
			return StageEventPayloadCodec.encode(this);
		}
	}

	/**
	 * 清除方块（CLEAR → AIR）。
	 */
	record Clear(
		@NonNull String animationType,
		@NonNull String targetObject,
		float energy,
		double durationSeconds,
		@NonNull TimelineEventOrigin eventOrigin,
		float energyThreshold,
		@NonNull Map<String, Object> extensions
	) implements StageEventPayload {

		public Clear {
			animationType = animationType != null ? animationType : "";
			targetObject = targetObject != null ? targetObject : "";
			energy = clamp01(energy);
			durationSeconds = Math.max(0.01, durationSeconds);
			eventOrigin = eventOrigin != null ? eventOrigin : TimelineEventOrigin.MANUAL;
			energyThreshold = clamp01(energyThreshold);
			extensions = extensions != null ? Map.copyOf(extensions) : Map.of();
		}

		@Override
		public @NonNull TimelineAnimationActionMode actionMode() {
			return TimelineAnimationActionMode.CLEAR;
		}

		@Override
		public @NonNull Map<String, Object> toParameterMap() {
			return StageEventPayloadCodec.encode(this);
		}
	}

	private static float clamp01(float v) {
		return Math.max(0f, Math.min(1f, v));
	}

	/** 空扩展只读视图。 */
	static @NonNull Map<String, Object> emptyExtensions() {
		return Collections.emptyMap();
	}
}
