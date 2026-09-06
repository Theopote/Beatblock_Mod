package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.marker.SectionMarkerStructureBridge;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 移动时间轴标记：execute 写入 {@code after}，undo 恢复 {@code before}（含 editState）。
 * SECTION 提交后通过 {@link SectionMarkerStructureBridge} 投影到 Music Structure，避免静默漂移。
 */
public final class MoveMarkerCommand implements Command {

	private final Timeline timeline;
	private final TimelineMarker before;
	private final TimelineMarker after;

	public MoveMarkerCommand(
		@NonNull Timeline timeline,
		@NonNull TimelineMarker before,
		double newTimeSeconds
	) {
		this.timeline = Objects.requireNonNull(timeline, "timeline");
		this.before = Objects.requireNonNull(before, "before");
		this.after = before.withTimeSeconds(newTimeSeconds, true);
	}

	/** @deprecated 使用 {@link #MoveMarkerCommand(Timeline, TimelineMarker, double)} 以保留 provenance。 */
	@Deprecated
	public MoveMarkerCommand(
		@NonNull Timeline timeline,
		@NonNull String markerId,
		double oldTimeSeconds,
		double newTimeSeconds,
		@Nullable String name
	) {
		this.timeline = Objects.requireNonNull(timeline, "timeline");
		int index = timeline.findMarkerIndexById(markerId);
		TimelineMarker current = index >= 0 ? timeline.getMarkers().get(index) : null;
		if (current != null) {
			this.before = current.withTimeSeconds(oldTimeSeconds, false);
			this.after = current.withFields(newTimeSeconds, name != null ? name : current.getName(), current.getType(), true);
		} else {
			this.before = new TimelineMarker(markerId, oldTimeSeconds, name);
			this.after = before.withTimeSeconds(newTimeSeconds, true);
		}
	}

	@Override
	public void execute() {
		if (!timeline.replaceMarker(after)) {
			timeline.addMarker(after);
		}
		if (after.getType().isStructural()) {
			SectionMarkerStructureBridge.projectMarkerOntoPlan(
				timeline, before.getTimeSeconds(), after);
		}
	}

	@Override
	public void undo() {
		if (!timeline.replaceMarker(before)) {
			timeline.addMarker(before);
		}
		if (before.getType().isStructural()) {
			SectionMarkerStructureBridge.projectMarkerOntoPlan(
				timeline, after.getTimeSeconds(), before);
		}
	}

	public String markerId() {
		return before.getId();
	}

	public double oldTimeSeconds() {
		return before.getTimeSeconds();
	}

	public double newTimeSeconds() {
		return after.getTimeSeconds();
	}

	public TimelineMarker before() {
		return before;
	}

	public TimelineMarker after() {
		return after;
	}
}
