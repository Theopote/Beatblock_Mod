package com.beatblock.timeline;

import com.beatblock.timeline.payload.StageEventPayload;
import com.beatblock.timeline.payload.StageEventPayloadCodec;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * 第 2 层 — 舞台事件（概念上的 StageEvent）。
 * <p>
 * 描述「在时间点对哪个舞台对象执行何种动作」。由创作者编辑或自动映射生成初稿；
 * 第 3 层 {@link com.beatblock.engine.BlockAnimationEngine} 在播放时消费，不区分来源。
 * <p>
 * 可带 energy 用于高度/速度/粒子数等映射。
 * <p>
 * 参数访问优先 {@link #getPayload()}（强类型）；{@link #getParameters()} 保留给持久化与兼容路径。
 */
public final class TimelineAnimationEvent {

	private final String eventId;
	private final double timeSeconds;
	private final double durationSeconds;
	private final String animationTypeId;
	private final String targetObjectId;
	private final float energy;
	private final Map<String, Object> parameters;
	/** 懒解析的强类型载荷（parameters 不可变，缓存安全）。 */
	private transient @Nullable StageEventPayload payloadCache;

	public TimelineAnimationEvent(
		@Nullable String eventId,
		double timeSeconds,
		double durationSeconds,
		@Nullable String animationTypeId,
		@Nullable String targetObjectId,
		float energy,
		@Nullable Map<String, Object> parameters
	) {
		this.eventId = eventId != null ? eventId : "";
		this.timeSeconds = timeSeconds;
		this.durationSeconds = Math.max(0.01, durationSeconds);
		this.animationTypeId = animationTypeId != null ? animationTypeId : "";
		this.targetObjectId = targetObjectId != null ? targetObjectId : "";
		this.energy = Math.max(0f, Math.min(1f, energy));
		this.parameters = parameters != null ? Map.copyOf(parameters) : Collections.emptyMap();
	}

	/**
	 * 从强类型载荷构造事件（序列化路径写回 Map，与 .osc 兼容）。
	 */
	public static @NonNull TimelineAnimationEvent fromPayload(
		@Nullable String eventId,
		double timeSeconds,
		@NonNull StageEventPayload payload
	) {
		if (payload == null) {
			throw new IllegalArgumentException("payload");
		}
		return new TimelineAnimationEvent(
			eventId,
			timeSeconds,
			payload.durationSeconds(),
			payload.animationType(),
			payload.targetObject(),
			payload.energy(),
			payload.toParameterMap()
		);
	}

	public @NonNull String getEventId() {
		return eventId;
	}

	public double getTimeSeconds() {
		return timeSeconds;
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public double getEndTimeSeconds() {
		return timeSeconds + durationSeconds;
	}

	public @NonNull String getAnimationTypeId() {
		return animationTypeId;
	}

	public @NonNull String getTargetObjectId() {
		return targetObjectId;
	}

	/**
	 * Unbound StageEvent: preset + time committed, target to be assigned later
	 * (Animation Library drag UX — see {@code docs/animation-library-drag-ux.md}).
	 */
	public boolean isUnboundTarget() {
		return targetObjectId == null || targetObjectId.isBlank();
	}

	public float getEnergy() {
		return energy;
	}

	/**
	 * 强类型参数视图。播放器与引擎应优先使用本方法，避免字符串键拼写错误。
	 */
	public @NonNull StageEventPayload getPayload() {
		StageEventPayload cached = payloadCache;
		if (cached == null) {
			cached = StageEventPayloadCodec.fromAnimationEvent(this);
			payloadCache = cached;
		}
		return cached;
	}

	public @NonNull AnimationEventParams toAnimationEventParams() {
		return AnimationEventParams.fromAnimationEvent(this);
	}

	public @NonNull TimelineAnimationActionMode getActionMode() {
		return getPayload().actionMode();
	}

	public @NonNull Map<String, Object> getParameters() {
		return parameters;
	}

	public @NonNull TimelineEventOrigin getEventOrigin() {
		return getPayload().eventOrigin();
	}
}
