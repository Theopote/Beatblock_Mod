package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.command.Command;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.CompositeCommand;
import com.beatblock.timeline.command.UpdateAnimationEventCommand;
import com.beatblock.timeline.editing.AnimationEventSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Converts safe validation repairs into one undoable CommandManager transaction. */
public final class TimelineAutoRepair {
	public record RepairResult(List<String> repairedEventIds, List<TimelineDiagnostic> unresolved) {
		public RepairResult {
			repairedEventIds = List.copyOf(repairedEventIds != null ? repairedEventIds : List.of());
			unresolved = List.copyOf(unresolved != null ? unresolved : List.of());
		}
	}

	private TimelineAutoRepair() {}

	public static RepairResult apply(
		Timeline timeline,
		TimelineValidationReport report,
		@Nullable BlockAnimationEngine engine,
		CommandManager commandManager
	) {
		Objects.requireNonNull(timeline, "timeline");
		Objects.requireNonNull(report, "report");
		Objects.requireNonNull(commandManager, "commandManager");
		List<Command> repairs = new ArrayList<>();
		List<String> repairedIds = new ArrayList<>();
		List<TimelineDiagnostic> unresolved = new ArrayList<>();
		Set<String> handledIds = new HashSet<>();

		for (TimelineDiagnostic diagnostic : report.problems()) {
			String eventId = diagnostic.eventId();
			if ((TimelineValidator.RULE_NON_POSITIVE_EVENT_DURATION.equals(diagnostic.ruleId())
				|| TimelineValidator.RULE_INVALID_DURATION.equals(diagnostic.ruleId()))
				&& eventId != null && handledIds.add(eventId)) {
				LocatedEvent located = locate(timeline, eventId);
				if (located != null) {
					repairs.add(durationRepair(timeline, located, defaultDuration(located.event(), engine)));
					repairedIds.add(eventId);
					continue;
				}
			}
			unresolved.add(diagnostic);
		}

		if (!repairs.isEmpty()) {
			commandManager.execute(new CompositeCommand(repairs));
		}
		return new RepairResult(repairedIds, unresolved);
	}

	private static Command durationRepair(Timeline timeline, LocatedEvent located, double duration) {
		AnimationEventSnapshot before = AnimationEventSnapshot.capture(
			located.event(), located.clip(), timeline, located.clip().getId());
		Map<String, Object> parameters = new HashMap<>(before.parameters());
		parameters.put("durationSeconds", duration);
		AnimationEventSnapshot after = new AnimationEventSnapshot(
			before.timeSeconds(), parameters, before.clipStartSeconds(), before.clipEndSeconds(),
			before.clipEventTimesById(), before.timelineMetadata(), before.timelineDurationSeconds());
		return new UpdateAnimationEventCommand(
			timeline, located.track().getId(), located.clip().getId(), located.event().getId(), before, after);
	}

	private static double defaultDuration(TimelineEvent event, @Nullable BlockAnimationEngine engine) {
		if (engine != null) {
			Object raw = event.getParameters().get("animationType");
			if (raw != null) {
				var definition = engine.getAnimationLibrary().get(String.valueOf(raw));
				if (definition != null && Double.isFinite(definition.getDurationSeconds())
					&& definition.getDurationSeconds() > 0) return definition.getDurationSeconds();
			}
		}
		return 1.0;
	}

	private static @Nullable LocatedEvent locate(Timeline timeline, String eventId) {
		for (Track track : timeline.getTracks()) {
			if (track == null || !Timeline.isAnimationEventsTrackId(track.getId())) continue;
			for (Clip clip : track.getClips()) {
				if (clip == null) continue;
				TimelineEvent event = clip.getEvent(eventId);
				if (event != null) return new LocatedEvent(track, clip, event);
			}
		}
		return null;
	}

	private record LocatedEvent(Track track, Clip clip, TimelineEvent event) {}
}