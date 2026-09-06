package com.beatblock.timeline;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 时间线标记点（Marker）：用于快速定位段落、镜头点、Drop、转场等关键时刻。
 * <p>
 * {@link MarkerType#SECTION} 同时承载结构语义（Animation Binding / Section Filter）；
 * {@link MarkerOrigin} / {@link MarkerEditState} 区分导航注释与受保护的结构对象。
 */
public final class TimelineMarker {

	private final String id;
	private final double timeSeconds;
	private final String name;
	private final MarkerType type;
	private final MarkerOrigin origin;
	private final MarkerEditState editState;

	public TimelineMarker(double timeSeconds, @Nullable String name) {
		this(UUID.randomUUID().toString(), timeSeconds, name, MarkerType.GENERIC,
			MarkerOrigin.MANUAL, MarkerEditState.USER_EDITED);
	}

	public TimelineMarker(@Nullable String id, double timeSeconds, @Nullable String name) {
		this(id, timeSeconds, name, MarkerType.GENERIC, MarkerOrigin.MANUAL, MarkerEditState.USER_EDITED);
	}

	public TimelineMarker(double timeSeconds, @Nullable String name, @Nullable MarkerType type) {
		this(UUID.randomUUID().toString(), timeSeconds, name, type,
			MarkerOrigin.MANUAL, MarkerEditState.USER_EDITED);
	}

	public TimelineMarker(@Nullable String id, double timeSeconds, @Nullable String name, @Nullable MarkerType type) {
		this(id, timeSeconds, name, type, MarkerOrigin.MANUAL, MarkerEditState.USER_EDITED);
	}

	public TimelineMarker(
		@Nullable String id,
		double timeSeconds,
		@Nullable String name,
		@Nullable MarkerType type,
		@Nullable MarkerOrigin origin,
		@Nullable MarkerEditState editState
	) {
		this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
		this.timeSeconds = Math.max(0, timeSeconds);
		this.name = name != null ? name : "";
		this.type = type != null ? type : MarkerType.GENERIC;
		this.origin = origin != null ? origin : MarkerOrigin.MANUAL;
		this.editState = editState != null ? editState : defaultEditState(this.origin);
	}

	/** 音频分析写入的 SECTION marker。 */
	public static TimelineMarker audioAnalysisSection(double timeSeconds, @Nullable String name) {
		return new TimelineMarker(
			null,
			timeSeconds,
			name,
			MarkerType.SECTION,
			MarkerOrigin.AUDIO_ANALYSIS,
			MarkerEditState.GENERATED
		);
	}

	/** 手工创建入口。 */
	public static TimelineMarker manual(double timeSeconds, @Nullable String name, @Nullable MarkerType type) {
		return new TimelineMarker(
			null,
			timeSeconds,
			name,
			type,
			MarkerOrigin.MANUAL,
			MarkerEditState.USER_EDITED
		);
	}

	public @NonNull String getId() {
		return id;
	}

	public double getTimeSeconds() {
		return timeSeconds;
	}

	public @NonNull String getName() {
		return name;
	}

	public @NonNull MarkerType getType() {
		return type;
	}

	public @NonNull MarkerOrigin getOrigin() {
		return origin;
	}

	public @NonNull MarkerEditState getEditState() {
		return editState;
	}

	/**
	 * @param promoteIfGenerated 为 true 且当前为 {@link MarkerEditState#GENERATED} 时，字段变化升为 USER_EDITED
	 */
	public TimelineMarker withFields(
		double timeSeconds,
		@Nullable String name,
		@Nullable MarkerType type,
		boolean promoteIfGenerated
	) {
		String nextName = name != null ? name : "";
		MarkerType nextType = type != null ? type : MarkerType.GENERIC;
		double nextTime = Math.max(0, timeSeconds);
		MarkerEditState nextState = this.editState;
		if (promoteIfGenerated
			&& this.editState == MarkerEditState.GENERATED
			&& hasMeaningfulChange(nextTime, nextName, nextType)) {
			nextState = MarkerEditState.USER_EDITED;
		}
		return new TimelineMarker(this.id, nextTime, nextName, nextType, this.origin, nextState);
	}

	public TimelineMarker withTimeSeconds(double timeSeconds, boolean promoteIfGenerated) {
		return withFields(timeSeconds, this.name, this.type, promoteIfGenerated);
	}

	public TimelineMarker withEditState(@Nullable MarkerEditState editState) {
		return new TimelineMarker(
			this.id,
			this.timeSeconds,
			this.name,
			this.type,
			this.origin,
			editState != null ? editState : this.editState
		);
	}

	private boolean hasMeaningfulChange(double nextTime, String nextName, MarkerType nextType) {
		return Double.compare(nextTime, this.timeSeconds) != 0
			|| !nextName.equals(this.name)
			|| nextType != this.type;
	}

	private static MarkerEditState defaultEditState(MarkerOrigin origin) {
		return origin != null && origin.isSystemProduced()
			? MarkerEditState.GENERATED
			: MarkerEditState.USER_EDITED;
	}
}
