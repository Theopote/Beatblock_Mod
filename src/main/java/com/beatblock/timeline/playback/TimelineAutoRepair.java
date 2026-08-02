package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.command.Command;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.CompositeCommand;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Converts safe validation repairs into one undoable CommandManager transaction. */
public final class TimelineAutoRepair {
	public enum RepairDisposition {
		SAFE_AUTOMATIC,
		REQUIRES_USER_INPUT,
		NOT_REPAIRABLE
	}

	public static RepairDisposition disposition(TimelineDiagnostic diagnostic) {
		if (diagnostic == null) return RepairDisposition.NOT_REPAIRABLE;
		String rule = diagnostic.ruleId();
		if (TimelineValidator.RULE_NON_POSITIVE_EVENT_DURATION.equals(rule)
			|| TimelineValidator.RULE_INVALID_DURATION.equals(rule)) {
			return RepairDisposition.SAFE_AUTOMATIC;
		}
		if (TimelineValidator.RULE_MISSING_ANIMATION_PRESET.equals(rule)
			|| TimelineValidator.RULE_UNBOUND_TARGET.equals(rule)
			|| TimelineValidator.RULE_MISSING_STAGE_OBJECT.equals(rule)
			|| TimelineValidator.RULE_MISSING_AUDIO.equals(rule)
			|| TimelineValidator.RULE_AUDIO_FILE_MISSING.equals(rule)
			|| TimelineValidator.RULE_MISSING_BUILD_LAYER.equals(rule)) {
			return RepairDisposition.REQUIRES_USER_INPUT;
		}
		return RepairDisposition.NOT_REPAIRABLE;
	}

	public static boolean canSafelyRepair(TimelineDiagnostic diagnostic) {
		return disposition(diagnostic) == RepairDisposition.SAFE_AUTOMATIC
			&& (diagnostic.sourceLocation() != null || diagnostic.eventId() != null);
	}
	public record RepairResult(
		List<String> repairedEventIds,
		List<TimelineSourceLocation> repairedLocations,
		List<TimelineDiagnostic> unresolved
	) {
		public RepairResult {
			repairedEventIds = List.copyOf(repairedEventIds != null ? repairedEventIds : List.of());
			repairedLocations = List.copyOf(repairedLocations != null ? repairedLocations : List.of());
			unresolved = List.copyOf(unresolved != null ? unresolved : List.of());
		}

		public RepairResult(List<String> repairedEventIds, List<TimelineDiagnostic> unresolved) {
			this(repairedEventIds, List.of(), unresolved);
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
		List<TimelineSourceLocation> repairedLocations = new ArrayList<>();
		List<TimelineDiagnostic> unresolved = new ArrayList<>();
		Set<String> handledSources = new HashSet<>();

		for (TimelineDiagnostic diagnostic : report.problems()) {
			TimelineSourceLocation source = diagnostic.sourceLocation();
			String eventId = diagnostic.eventId();
			String sourceKey = source != null ? "source:" + source.sourceIndex() : "id:" + eventId;
			if ((TimelineValidator.RULE_NON_POSITIVE_EVENT_DURATION.equals(diagnostic.ruleId())
				|| TimelineValidator.RULE_INVALID_DURATION.equals(diagnostic.ruleId()))
				&& (source != null || eventId != null) && handledSources.add(sourceKey)) {
				LocatedEvent located = locate(timeline, source, eventId);
				if (located != null) {
					repairs.add(durationRepair(timeline, located, defaultDuration(located.event(), engine)));
					if (eventId != null) repairedIds.add(eventId);
					if (source != null) repairedLocations.add(source);
					continue;
				}
			}
			unresolved.add(diagnostic);
		}

		if (!repairs.isEmpty()) {
			commandManager.execute(new CompositeCommand(repairs));
		}
		return new RepairResult(repairedIds, repairedLocations, unresolved);
	}

	private static Command durationRepair(Timeline timeline, LocatedEvent located, double duration) {
		Map<String, Object> before = Map.copyOf(located.event().getParameters());
		Map<String, Object> after = new HashMap<>(before);
		after.put("durationSeconds", duration);
		return new Command() {
			@Override public void execute() { apply(after); }
			@Override public void undo() { apply(before); }
			private void apply(Map<String, Object> parameters) {
				located.event().setParameters(parameters);
				timeline.markAnimationEventsDirty(located.track().getId());
			}
		};
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

	private static @Nullable LocatedEvent locate(
		Timeline timeline, @Nullable TimelineSourceLocation source, @Nullable String eventId) {
		if (source != null) {
			List<LocatedEvent> candidates = sourceEvents(timeline);
			if (source.sourceIndex() < candidates.size()) {
				LocatedEvent candidate = candidates.get(source.sourceIndex());
				if (source.trackId().isBlank() || source.trackId().equals(candidate.track().getId())) return candidate;
			}
		}
		if (eventId == null) return null;
		for (LocatedEvent candidate : sourceEvents(timeline)) {
			if (eventId.equals(candidate.event().getId())) return candidate;
		}
		return null;
	}

	private static List<LocatedEvent> sourceEvents(Timeline timeline) {
		record Ordered(LocatedEvent located, int order) {}
		List<Ordered> ordered = new ArrayList<>();
		int order = 0;
		for (Track track : timeline.getTracks()) {
			if (track == null || !Timeline.isAnimationEventsTrackId(track.getId())) continue;
			for (Clip clip : track.getClips()) {
				if (clip == null) continue;
				for (TimelineEvent event : clip.getEvents()) {
					if (event != null && event.getType() == com.beatblock.timeline.EventType.ANIMATION) {
						ordered.add(new Ordered(new LocatedEvent(track, clip, event), order++));
					}
				}
			}
		}
		ordered.sort(Comparator.comparingDouble((Ordered value) -> value.located().event().getTimeSeconds())
			.thenComparingInt(Ordered::order));
		return ordered.stream().map(Ordered::located).toList();
	}
	private record LocatedEvent(Track track, Clip clip, TimelineEvent event) {}
}