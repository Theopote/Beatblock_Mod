package com.beatblock.timeline.playback;

import com.beatblock.timeline.TimelineAnimationEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Formal-playback engine: advances only over a {@link CompiledTimelineSnapshot}.
 * <p>
 * Does not read the live editable {@code Timeline}. Preview paths stay in
 * {@code BeatBlockClientDriver} and may use the document directly.
 * <p>
 * Stage dispatch uses a dual-pointer cursor over the pre-sorted event list (O(k) per frame).
 * Compiled stage events are indexed by id for O(1) lookup.
 */
public final class PlaybackEngine {

	public static final double EVENT_EPSILON = 1e-4;

	/** Sink for due stage events (world / animation side effects stay outside). */
	@FunctionalInterface
	public interface StageEventHandler {
		void onStageEvent(CompiledStageEvent compiled, TimelineAnimationEvent event);
	}

	/** Sink for due global/VFX cues. */
	@FunctionalInterface
	public interface GlobalEventHandler {
		void onGlobalEvent(CompiledGlobalEvent event);
	}

	private @Nullable CompiledTimelineSnapshot program;
	private final Map<String, CompiledStageEvent> stageById = new HashMap<>();
	private final Set<String> scheduledStageIds = new HashSet<>();
	private final Set<String> scheduledGlobalIds = new HashSet<>();
	private int stageCursor;
	private int globalCursor;
	private double lastTime;

	public void load(@Nullable CompiledTimelineSnapshot snapshot) {
		reset();
		this.program = snapshot;
		if (snapshot == null) {
			return;
		}
		for (CompiledStageEvent compiled : snapshot.compiledStageEvents()) {
			if (compiled == null || compiled.event() == null) {
				continue;
			}
			String id = compiled.event().getEventId();
			if (id != null && !id.isBlank()) {
				stageById.put(id, compiled);
			}
		}
	}

	public void reset() {
		program = null;
		stageById.clear();
		scheduledStageIds.clear();
		scheduledGlobalIds.clear();
		stageCursor = 0;
		globalCursor = 0;
		lastTime = 0;
	}

	public @Nullable CompiledTimelineSnapshot program() {
		return program;
	}

	public boolean isLoaded() {
		return program != null;
	}

	public @Nullable CompiledStageEvent findCompiledStage(String eventId) {
		if (eventId == null || eventId.isBlank()) {
			return null;
		}
		return stageById.get(eventId);
	}

	/**
	 * Advance formal playback to {@code currentTime}, invoking handlers for newly due events.
	 * On rewind ({@code currentTime < lastTime - epsilon}), cursors and scheduled sets reset.
	 */
	public void advance(
		double currentTime,
		@Nullable StageEventHandler stageHandler,
		@Nullable GlobalEventHandler globalHandler
	) {
		if (program == null) {
			return;
		}
		if (currentTime + EVENT_EPSILON < lastTime) {
			scheduledStageIds.clear();
			scheduledGlobalIds.clear();
			stageCursor = 0;
			globalCursor = 0;
		}

		List<CompiledStageEvent> stages = program.compiledStageEvents();
		if (stageCursor < 0 || stageCursor > stages.size()) {
			stageCursor = 0;
		}
		while (stageCursor < stages.size()) {
			CompiledStageEvent compiled = stages.get(stageCursor);
			TimelineAnimationEvent event = compiled != null ? compiled.event() : null;
			if (event == null) {
				stageCursor++;
				continue;
			}
			if (event.getTimeSeconds() > currentTime + EVENT_EPSILON) {
				break;
			}
			String key = scheduleKey(event);
			if (scheduledStageIds.add(key) && stageHandler != null) {
				stageHandler.onStageEvent(compiled, event);
			}
			stageCursor++;
		}

		List<CompiledGlobalEvent> globals = program.globalEvents();
		if (globalCursor < 0 || globalCursor > globals.size()) {
			globalCursor = 0;
		}
		while (globalCursor < globals.size()) {
			CompiledGlobalEvent ge = globals.get(globalCursor);
			if (ge == null) {
				globalCursor++;
				continue;
			}
			if (ge.timeSeconds() > currentTime + EVENT_EPSILON) {
				break;
			}
			String key = globalKey(ge);
			if (scheduledGlobalIds.add(key) && globalHandler != null) {
				globalHandler.onGlobalEvent(ge);
			}
			globalCursor++;
		}

		lastTime = currentTime;
	}

	/** Unit-test helper: how many stage events have been scheduled since load/reset. */
	public int scheduledStageCount() {
		return scheduledStageIds.size();
	}

	public int scheduledGlobalCount() {
		return scheduledGlobalIds.size();
	}

	private static String scheduleKey(TimelineAnimationEvent event) {
		if (event.getEventId() != null && !event.getEventId().isBlank()) {
			return event.getEventId();
		}
		return event.getTimeSeconds() + "|" + event.getAnimationTypeId() + "|" + event.getTargetObjectId();
	}

	private static String globalKey(CompiledGlobalEvent event) {
		if (event.id() != null && !event.id().isBlank()) {
			return event.id();
		}
		return event.timeSeconds() + "|" + event.typeName() + "|" + event.name();
	}

	/** Build a stable synthetic id for global events that lack one. */
	public static String syntheticGlobalId(double time, String type, String name, int index) {
		return "global:" + index + ":" + time + ":" + Objects.toString(type, "") + ":" + Objects.toString(name, "");
	}
}
