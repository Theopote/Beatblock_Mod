package com.beatblock.timeline.command;

import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量更新动画事件命令：一次选中多个事件（时间轴框选/Ctrl 多选），同时修改它们共有的属性。
 */
public final class BatchUpdateEventsCommand implements Command {

	private final Timeline timeline;
	private final String trackId;
	private final List<TimelineAnimationEvent> targetEvents;
	private final BatchUpdateOptions options;

	/** 每个被实际改动的事件的「改之前」快照，用于 undo；定位失败的事件不会出现在这里。 */
	private final List<EventSnapshot> snapshots = new ArrayList<>();
	private boolean executed = false;

	public BatchUpdateEventsCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull List<TimelineAnimationEvent> events,
		@NonNull BatchUpdateOptions options
	) {
		this.timeline = timeline;
		this.trackId = trackId;
		this.targetEvents = new ArrayList<>(events);
		this.options = options;
	}

	@Override
	public void execute() {
		if (executed) return;

		snapshots.clear();
		for (TimelineAnimationEvent event : targetEvents) {
			AnimationEventLocator.Located located =
				AnimationEventLocator.locate(timeline, trackId, event.getEventId());
			if (located == null) continue;

			Map<String, Object> before = Map.copyOf(located.event().getParameters());
			AnimationEventParams params = AnimationEventParams.fromParameterMap(before);
			AnimationEventParams updated = options.applyTo(params);
			AnimationEventLocator.applyParameters(timeline, trackId, located.event(), updated.toParameterMap());

			snapshots.add(new EventSnapshot(located.clipId(), located.event(), before));
		}

		executed = true;
	}

	@Override
	public void undo() {
		if (!executed) return;

		for (EventSnapshot snapshot : snapshots) {
			snapshot.restore(timeline, trackId);
		}
		snapshots.clear();

		executed = false;
	}

	/**
	 * 批量更新选项。每个字段独立可选（null = 不改这一项），可以同时设置多项
	 * （比如一次操作既改强度又整体缩放时长）。
	 */
	public static final class BatchUpdateOptions {
		private @Nullable Float energy;
		private @Nullable Double durationSeconds;
		private @Nullable Double durationScale;
		private @Nullable String animationTypeId;
		private @Nullable TimelineAnimationActionMode actionMode;
		private final Map<String, Object> customParameters = new HashMap<>();

		public BatchUpdateOptions setEnergy(float energy) {
			this.energy = energy;
			return this;
		}

		public BatchUpdateOptions setDuration(double seconds) {
			this.durationSeconds = seconds;
			return this;
		}

		public BatchUpdateOptions scaleDuration(double factor) {
			this.durationScale = factor;
			return this;
		}

		public BatchUpdateOptions setAnimationType(String typeId) {
			this.animationTypeId = typeId;
			return this;
		}

		public BatchUpdateOptions setActionMode(TimelineAnimationActionMode mode) {
			this.actionMode = mode;
			return this;
		}

		public BatchUpdateOptions setParameter(String key, Object value) {
			if (key != null && !key.isBlank()) {
				customParameters.put(key, value);
			}
			return this;
		}

		AnimationEventParams applyTo(AnimationEventParams source) {
			double resolvedDuration = source.durationSeconds();
			if (durationScale != null) {
				resolvedDuration = resolvedDuration * durationScale;
			}
			if (durationSeconds != null) {
				resolvedDuration = durationSeconds;
			}

			AnimationEventParams result = new AnimationEventParams(
				actionMode != null ? actionMode : source.actionMode(),
				animationTypeId != null ? animationTypeId : source.animationType(),
				source.targetObject(),
				energy != null ? energy : source.energy(),
				resolvedDuration,
				source.eventOrigin(),
				source.extensions()
			);

			if (!customParameters.isEmpty()) {
				result = result.withMergedExtensions(customParameters);
			}
			return result;
		}
	}

	private record EventSnapshot(String clipId, TimelineEvent event, Map<String, Object> originalParameters) {
		void restore(Timeline timeline, String trackId) {
			AnimationEventLocator.applyParameters(timeline, trackId, event, originalParameters);
		}
	}
}
