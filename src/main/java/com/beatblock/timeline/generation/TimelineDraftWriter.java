package com.beatblock.timeline.generation;

import com.beatblock.BeatBlock;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.command.AddTimelineAnimationEventCommand;
import com.beatblock.timeline.command.ClearAnimationTrackCommand;
import com.beatblock.timeline.command.Command;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.CompositeCommand;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Timeline animation-event insertion gateway.
 * <p>
 * Shared transaction semantics for Animation Library, Event Library, Record Mode,
 * RhythmDrop, binding, feature mapping, and auto-generation:
 * origin tagging, {@link CompositeCommand} batching, dirty marks, and (for manual
 * inserts) {@link TimelineDocumentChangeNotifier}.
 * <ul>
 *   <li>{@link #insertManualEvents} — UI-committed inserts; notifies document change</li>
 *   <li>{@link #insertGeneratedEvents} — generator / draft writes; no document notify</li>
 *   <li>{@link #replaceGeneratedEvents} — clear AUTO track + insert as one undo</li>
 * </ul>
 */
public final class TimelineDraftWriter {

	private TimelineDraftWriter() {}

	/** UI-triggered single insert; notifies document change when written. */
	public static boolean insertManualEvent(
		@Nullable Timeline timeline,
		@Nullable String trackId,
		@Nullable TimelineAnimationEvent event
	) {
		if (event == null) return false;
		return insertManualEvents(timeline, trackId, List.of(event)) > 0;
	}

	/**
	 * UI-triggered batch insert as one {@link CompositeCommand}.
	 * Notifies document change once when {@code written > 0}.
	 */
	public static int insertManualEvents(
		@Nullable Timeline timeline,
		@Nullable String trackId,
		@Nullable List<TimelineAnimationEvent> events
	) {
		int written = insertEvents(timeline, trackId, events, TimelineEventOrigin.MANUAL);
		if (written > 0) {
			TimelineDocumentChangeNotifier.notifyDocumentEdited();
		}
		return written;
	}

	/** Generator / draft single insert; does not notify document change. */
	public static boolean insertGeneratedEvent(
		@Nullable Timeline timeline,
		@Nullable String trackId,
		@Nullable TimelineAnimationEvent event
	) {
		if (event == null) return false;
		return insertGeneratedEvents(timeline, trackId, List.of(event)) > 0;
	}

	/**
	 * Generator / draft batch insert as one {@link CompositeCommand}.
	 * Does not notify document change — callers that expose a user commit should
	 * notify at their own boundary.
	 */
	public static int insertGeneratedEvents(
		@Nullable Timeline timeline,
		@Nullable String trackId,
		@Nullable List<TimelineAnimationEvent> events
	) {
		return insertEvents(timeline, trackId, events, TimelineEventOrigin.GENERATED);
	}

	/**
	 * Clear the AUTO animation track and insert {@code events} as one undo step.
	 * Returns the number of events inserted (0 if only cleared / empty).
	 */
	public static int replaceGeneratedEvents(
		@Nullable Timeline timeline,
		@Nullable List<TimelineAnimationEvent> events
	) {
		if (timeline == null) return 0;
		String trackId = Timeline.TRACK_ID_ANIMATION_AUTO;
		List<Command> parts = new ArrayList<>();
		parts.add(new ClearAnimationTrackCommand(timeline, trackId));
		List<Command> inserts = buildAddCommands(timeline, trackId, events, TimelineEventOrigin.GENERATED);
		parts.addAll(inserts);
		executeCommands(parts);
		if (!inserts.isEmpty()) {
			timeline.sortAll();
		}
		return inserts.size();
	}

	public static void clearTrack(@Nullable Timeline timeline, @Nullable String trackId) {
		if (timeline == null || trackId == null) return;
		executeCommands(List.of(new ClearAnimationTrackCommand(timeline, trackId)));
	}

	public static @Nullable TimelineAnimationEvent withOrigin(
		@Nullable TimelineAnimationEvent source,
		@Nullable TimelineEventOrigin origin
	) {
		if (source == null) return null;
		TimelineEventOrigin resolved = origin != null ? origin : TimelineEventOrigin.GENERATED;
		return withMetadata(source, TimelineGenerationMetadata.fromOrigin(resolved));
	}

	public static @Nullable TimelineAnimationEvent withMetadata(
		@Nullable TimelineAnimationEvent source,
		@Nullable TimelineGenerationMetadata metadata
	) {
		if (source == null) return null;
		Map<String, Object> params = TimelineGenerationMetadataSupport.apply(
			AnimationEventParams.fromAnimationEvent(source).toParameterMap(),
			metadata
		);
		AnimationEventParams parsed = AnimationEventParams.fromParameterMap(params);
		return new TimelineAnimationEvent(
			source.getEventId(),
			source.getTimeSeconds(),
			parsed.durationSeconds(),
			parsed.animationType(),
			parsed.targetObject(),
			parsed.energy(),
			parsed.toParameterMap()
		);
	}

	private static int insertEvents(
		@Nullable Timeline timeline,
		@Nullable String trackId,
		@Nullable List<TimelineAnimationEvent> events,
		TimelineEventOrigin origin
	) {
		if (timeline == null || trackId == null || events == null || events.isEmpty()) return 0;
		List<Command> parts = buildAddCommands(timeline, trackId, events, origin);
		if (parts.isEmpty()) return 0;
		executeCommands(parts);
		timeline.sortAll();
		return parts.size();
	}

	private static List<Command> buildAddCommands(
		Timeline timeline,
		String trackId,
		@Nullable List<TimelineAnimationEvent> events,
		TimelineEventOrigin origin
	) {
		if (events == null || events.isEmpty()) return List.of();
		List<Command> parts = new ArrayList<>(events.size());
		for (TimelineAnimationEvent event : events) {
			if (event == null) continue;
			TimelineAnimationEvent tagged = tagForWrite(event, origin);
			if (tagged == null) continue;
			parts.add(new AddTimelineAnimationEventCommand(timeline, trackId, tagged));
		}
		return parts;
	}

	private static void executeCommands(List<Command> parts) {
		if (parts.isEmpty()) return;
		Command batch = parts.size() == 1 ? parts.getFirst() : new CompositeCommand(parts);
		CommandManager commands = commandManagerOrNull();
		if (commands != null) {
			commands.execute(batch);
		} else {
			batch.execute();
		}
	}

	private static @Nullable TimelineAnimationEvent tagForWrite(
		TimelineAnimationEvent event,
		TimelineEventOrigin origin
	) {
		TimelineGenerationMetadata existing = TimelineGenerationMetadata.fromParameters(event.getParameters());
		if (existing.generatorId().isBlank() && existing.generationId().isBlank()) {
			return withOrigin(event, origin);
		}
		return event;
	}

	private static @Nullable CommandManager commandManagerOrNull() {
		return commandManagerOrNull(BeatBlock.getContext());
	}

	static @Nullable CommandManager commandManagerOrNull(@Nullable BeatBlockContext context) {
		if (context == null) return null;
		return context.commandManager();
	}
}
