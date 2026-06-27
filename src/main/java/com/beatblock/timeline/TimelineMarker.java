package com.beatblock.timeline;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 时间线标记点（Marker）：用于快速定位段落、镜头点、Drop、转场等关键时刻。
 */
public final class TimelineMarker {

	private final String id;
	private final double timeSeconds;
	private final String name;
	private final MarkerType type;

	public TimelineMarker(double timeSeconds, @Nullable String name) {
		this(UUID.randomUUID().toString(), timeSeconds, name, MarkerType.GENERIC);
	}

	public TimelineMarker(@Nullable String id, double timeSeconds, @Nullable String name) {
		this(id, timeSeconds, name, MarkerType.GENERIC);
	}

	public TimelineMarker(double timeSeconds, @Nullable String name, @Nullable MarkerType type) {
		this(UUID.randomUUID().toString(), timeSeconds, name, type);
	}

	public TimelineMarker(@Nullable String id, double timeSeconds, @Nullable String name, @Nullable MarkerType type) {
		this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
		this.timeSeconds = Math.max(0, timeSeconds);
		this.name = name != null ? name : "";
		this.type = type != null ? type : MarkerType.GENERIC;
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
}
