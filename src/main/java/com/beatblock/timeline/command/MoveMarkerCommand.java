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
 * <p>
 * Mutation must succeed before structure projection; failed collision leaves document untouched.
 */
public final class MoveMarkerCommand implements AppliedCommand {

	private final Timeline timeline;
	private final TimelineMarker before;
	private final TimelineMarker after;
	private boolean done;

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
	public boolean wasApplied() {
		return done;
	}

	@Override
	public void execute() {
		if (done) {
			return;
		}
		boolean applied = timeline.replaceMarker(after);
		if (!applied && timeline.findMarkerIndexById(after.getId()) < 0) {
			applied = timeline.addMarker(after);
		}
		if (!applied) {
			done = false;
			return;
		}
		if (after.getType().isStructural()) {
			SectionMarkerStructureBridge.projectMarkerOntoPlan(
				timeline, before.getTimeSeconds(), after);
		}
		done = true;
	}

	@Override
	public void undo() {
		if (!done) {
			return;
		}
		boolean restored = timeline.replaceMarker(before);
		if (!restored && timeline.findMarkerIndexById(before.getId()) < 0) {
			restored = timeline.addMarker(before);
		}
		if (restored && before.getType().isStructural()) {
			SectionMarkerStructureBridge.projectMarkerOntoPlan(
				timeline, after.getTimeSeconds(), before);
		}
		done = false;
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
