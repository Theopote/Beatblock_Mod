package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 移动时间轴标记：execute 设为 newTime，undo 恢复 oldTime。
 */
public final class MoveMarkerCommand implements Command {

	private final Timeline timeline;
	private final String markerId;
	private final double oldTimeSeconds;
	private final double newTimeSeconds;
	private final String name;

	public MoveMarkerCommand(
		@NonNull Timeline timeline,
		@NonNull String markerId,
		double oldTimeSeconds,
		double newTimeSeconds,
		@Nullable String name
	) {
		this.timeline = timeline;
		this.markerId = markerId;
		this.oldTimeSeconds = oldTimeSeconds;
		this.newTimeSeconds = newTimeSeconds;
		this.name = name != null ? name : "";
	}

	@Override
	public void execute() {
		timeline.updateMarker(markerId, newTimeSeconds, name);
	}

	@Override
	public void undo() {
		timeline.updateMarker(markerId, oldTimeSeconds, name);
	}

	public String markerId() {
		return markerId;
	}

	public double oldTimeSeconds() {
		return oldTimeSeconds;
	}

	public double newTimeSeconds() {
		return newTimeSeconds;
	}
}
