package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.GlobalEvent;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.payload.StageEventPayload;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles an editable {@link Timeline} into an immutable {@link CompiledTimelineSnapshot}
 * (Phase B: stage + camera + build layers + audio + markers + validation report).
 */
public final class TimelineCompiler {

	public static final int COMPILER_VERSION = 1;

	private TimelineCompiler() {}

	public static CompiledTimelineSnapshot compile(@Nullable Timeline document) {
		return compile(document, null, null);
	}

	public static CompiledTimelineSnapshot compile(
		@Nullable Timeline document,
		@Nullable BlockAnimationEngine engine
	) {
		return compile(document, engine, null);
	}

	/**
	 * Full compile with optional engine (stage/preset resolve) and layer manager (build layers).
	 * Always runs {@link TimelineValidator} and attaches the report to the snapshot.
	 */
	public static CompiledTimelineSnapshot compile(
		@Nullable Timeline document,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager
	) {
		return compile(document, engine, layerManager, CompilePolicy.STRICT).snapshot();
	}

	public static CompileResult compile(
		@Nullable Timeline document,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layerManager,
		CompilePolicy policy
	) {
		if (policy == null) {
			throw new IllegalArgumentException("policy must not be null");
		}
		if (document == null) {
			return new CompileResult(CompiledTimelineSnapshot.empty(), TimelineValidator.validate(null, engine, layerManager), List.of());
		}

		TimelineValidationReport report = TimelineValidator.validate(document, engine, layerManager);
		if (report.hasFatalErrors() || (policy == CompilePolicy.STRICT && report.hasErrors())) {
			throw new TimelineCompilationException(report);
		}

		SkipSelection skipSelection = degradableEvents(report, policy);
		List<String> skippedEventIds = new ArrayList<>();
		List<TimelineSourceLocation> skippedLocations = new ArrayList<>();
		List<TimelineAnimationEvent> sourceEvents = document.getStageEvents();
		List<TimelineAnimationEvent> events = new ArrayList<>();
		for (int sourceIndex = 0; sourceIndex < sourceEvents.size(); sourceIndex++) {
			TimelineAnimationEvent event = sourceEvents.get(sourceIndex);
			if (event == null) continue;
			boolean skip = skipSelection.sourceIndexes().contains(sourceIndex)
				|| (!event.getEventId().isBlank() && skipSelection.eventIds().contains(event.getEventId()));
			if (skip) {
				if (!event.getEventId().isBlank()) skippedEventIds.add(event.getEventId());
				TimelineSourceLocation location = skipSelection.locationsByIndex().get(sourceIndex);
				if (location == null) location = new TimelineSourceLocation("", "", event.getEventId(), sourceIndex);
				skippedLocations.add(location);
				continue;
			}
			events.add(copyEvent(event));
		}
		events.sort(Comparator
			.comparingDouble(TimelineAnimationEvent::getTimeSeconds)
			.thenComparing(TimelineAnimationEvent::getEventId));

		Set<String> stageEventIds = new HashSet<>();
		for (TimelineAnimationEvent sourceEvent : sourceEvents) {
			if (sourceEvent != null && !sourceEvent.getEventId().isBlank()) {
				stageEventIds.add(sourceEvent.getEventId());
			}
		}
		for (String skippedId : skipSelection.eventIds()) {
			if (!stageEventIds.contains(skippedId)) skippedEventIds.add(skippedId);
		}

		double bpm = document.getBpm() > 0 ? document.getBpm() : 120.0;
		double duration = Math.max(0, document.getDurationSeconds());

		CompiledTimelineSnapshot snapshot = new CompiledTimelineSnapshot(
			events,
			compileStageEvents(events, engine),
			compileCameraTrack(document.getTrack(Timeline.TRACK_ID_CAMERA), skipSelection.eventIds()),
			compileBuildLayers(layerManager),
			compileMarkers(document),
			compileGlobalEvents(document),
			compileAudio(document),
			ReferenceBeatResolver.resolveBeatTimesSeconds(document),
			bpm,
			duration,
			shouldRestoreWorldMutations(document),
			document.getStageEventsGeneration(),
			report
		);
		String sourceFingerprint = CompiledProgramFingerprint.compute(snapshot);
		snapshot.attachMetadata(new CompiledProgramMetadata(
			COMPILER_VERSION,
			String.valueOf(document.getMetadata("projectId") != null ? document.getMetadata("projectId") : ""),
			document.getStageEventsGeneration(),
			sourceFingerprint,
			presetCatalogFingerprint(engine)
		));
		return new CompileResult(snapshot, report, skippedEventIds, skippedLocations);
	}


	private static String presetCatalogFingerprint(@Nullable BlockAnimationEngine engine) {
		if (engine == null) return "";
		String ids = String.join("\n", engine.getAnimationLibrary().getAll().keySet().stream().sorted().toList());
		try {
			byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
				.digest(ids.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(bytes);
		} catch (java.security.NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	private static SkipSelection degradableEvents(TimelineValidationReport report, CompilePolicy policy) {
		if (policy != CompilePolicy.SKIP_INVALID_EVENTS) return SkipSelection.empty();
		Set<Integer> sourceIndexes = new HashSet<>();
		Set<String> eventIds = new HashSet<>();
		Map<Integer, TimelineSourceLocation> locationsByIndex = new LinkedHashMap<>();
		for (TimelineDiagnostic diagnostic : report.problems()) {
			String rule = diagnostic.ruleId();
			if (!isDegradableRule(rule)) continue;
			TimelineSourceLocation location = diagnostic.sourceLocation();
			if (location != null && Timeline.isAnimationEventsTrackId(location.trackId())) {
				sourceIndexes.add(location.sourceIndex());
				locationsByIndex.putIfAbsent(location.sourceIndex(), location);
			}
			if (diagnostic.eventId() != null) eventIds.add(diagnostic.eventId());
		}
		return new SkipSelection(sourceIndexes, eventIds, locationsByIndex);
	}

	private static boolean isDegradableRule(String rule) {
		return TimelineValidator.RULE_UNBOUND_TARGET.equals(rule)
			|| TimelineValidator.RULE_MISSING_STAGE_OBJECT.equals(rule)
			|| TimelineValidator.RULE_MISSING_ANIMATION_PRESET.equals(rule)
			|| TimelineValidator.RULE_MISSING_BUILD_LAYER.equals(rule)
			|| TimelineValidator.RULE_NON_POSITIVE_EVENT_DURATION.equals(rule)
			|| TimelineValidator.RULE_MISSING_CAMERA_SUBJECT.equals(rule)
			|| TimelineValidator.RULE_MISSING_CAMERA_LOOK_AT.equals(rule)
			|| TimelineValidator.RULE_MISSING_CAMERA_BUILD_LAYER.equals(rule)
			|| TimelineValidator.RULE_INVALID_CAMERA_FRAMING.equals(rule)
			|| TimelineValidator.RULE_UNSUPPORTED_CAMERA_TRANSITION.equals(rule);
	}

	private record SkipSelection(
		Set<Integer> sourceIndexes,
		Set<String> eventIds,
		Map<Integer, TimelineSourceLocation> locationsByIndex
	) {
		private static SkipSelection empty() {
			return new SkipSelection(Set.of(), Set.of(), Map.of());
		}
	}
	private static List<CompiledStageEvent> compileStageEvents(
		List<TimelineAnimationEvent> events,
		@Nullable BlockAnimationEngine engine
	) {
		List<CompiledStageEvent> compiled = new ArrayList<>(events.size());
		long sequence = 0;
		for (TimelineAnimationEvent event : events) {
			var definition = engine != null
				? engine.getAnimationLibrary().get(event.getAnimationTypeId())
				: null;
			RuntimeStageObject source = engine != null
				? engine.getStageObjectSystem().get(event.getTargetObjectId())
				: null;
			CompiledStageTarget target = null;
			if (source != null) {
				target = new CompiledStageTarget(
					source.getId(),
					source.getName(),
					source.getBlocks(),
					source.getCenter(),
					source.getGroupSpec().getSortingStrategy(),
					source.getGroupSpec().getStaggerDelaySeconds()
				);
			}
			compiled.add(new CompiledStageEvent(event, definition, target, sequence++));
		}
		return List.copyOf(compiled);
	}

	private static CompiledCameraTrack compileCameraTrack(@Nullable Track track, Set<String> skipEventIds) {
		if (track == null || !track.isEnabled()) {
			return new CompiledCameraTrack(List.of());
		}
		Set<String> skipped = skipEventIds != null ? skipEventIds : Set.of();
		List<CompiledCameraTrack.CameraClip> clips = new ArrayList<>();
		for (var clip : track.getClips()) {
			List<CompiledCameraTrack.CameraEvent> cameraEvents = new ArrayList<>();
			int sourceEventCount = 0;
			for (var event : clip.getEvents()) {
				if (event == null) continue;
				sourceEventCount++;
				if (!event.getId().isBlank() && skipped.contains(event.getId())) continue;
				cameraEvents.add(new CompiledCameraTrack.CameraEvent(
					event.getId(),
					event.getTimeSeconds(),
					event.getType(),
					freezeMap(event.getParameters())
				));
			}
			cameraEvents.sort(Comparator.comparingDouble(CompiledCameraTrack.CameraEvent::timeSeconds)
				.thenComparing(CompiledCameraTrack.CameraEvent::id));
			if (sourceEventCount > 0 && cameraEvents.isEmpty()) continue;
			clips.add(new CompiledCameraTrack.CameraClip(
				clip.getStartTimeSeconds(),
				clip.getEndTimeSeconds(),
				cameraEvents
			));
		}
		clips.sort(Comparator.comparingDouble(CompiledCameraTrack.CameraClip::startTimeSeconds));
		return new CompiledCameraTrack(clips);
	}

	private static List<CompiledBuildLayer> compileBuildLayers(@Nullable BuildLayerManager layerManager) {
		if (layerManager == null) {
			return List.of();
		}
		List<CompiledBuildLayer> out = new ArrayList<>();
		for (BuildLayer layer : layerManager.getAll()) {
			if (layer == null) {
				continue;
			}
			RuntimeStageObject stage = layer.getStageObject();
			List<net.minecraft.util.math.BlockPos> blocks = stage != null
				? List.copyOf(stage.getBlocks())
				: List.of();
			String state = layer.getState() != null ? layer.getState().name() : "FREE_VISIBLE";
			out.add(new CompiledBuildLayer(
				layer.getId(),
				layer.getName(),
				layer.getStageObjectId(),
				layer.getBoundClipId(),
				state,
				layer.getGroupId(),
				blocks
			));
		}
		out.sort(Comparator.comparing(CompiledBuildLayer::layerId));
		return List.copyOf(out);
	}

	private static List<CompiledMarker> compileMarkers(Timeline document) {
		List<CompiledMarker> out = new ArrayList<>();
		for (TimelineMarker marker : document.getMarkers()) {
			if (marker == null) {
				continue;
			}
			MarkerType type = marker.getType();
			out.add(new CompiledMarker(
				marker.getId(),
				marker.getTimeSeconds(),
				marker.getName(),
				type != null ? type.name() : "GENERIC"
			));
		}
		out.sort(Comparator
			.comparingDouble(CompiledMarker::timeSeconds)
			.thenComparing(CompiledMarker::id));
		return List.copyOf(out);
	}

	/** Global track → immutable VFX/cue list (sorted by time). Prefer track events for stable ids. */
	private static List<CompiledGlobalEvent> compileGlobalEvents(Timeline document) {
		List<CompiledGlobalEvent> out = new ArrayList<>();
		int index = 0;
		Track globalTrack = document.getTrack(Timeline.TRACK_ID_GLOBAL);
		if (globalTrack != null) {
			for (var clip : globalTrack.getClips()) {
				if (clip == null) continue;
				for (var event : clip.getEvents()) {
					if (event == null || event.getType() != EventType.GLOBAL) continue;
					String typeName = String.valueOf(event.getParameters().getOrDefault("type", "SPECIAL"));
					String name = String.valueOf(event.getParameters().getOrDefault("name", ""));
					String id = event.getId() != null && !event.getId().isBlank()
						? event.getId()
						: PlaybackEngine.syntheticGlobalId(event.getTimeSeconds(), typeName, name, index++);
					out.add(new CompiledGlobalEvent(
						 id,
						 event.getTimeSeconds(),
						 GlobalEventPayloadCodec.decode(freezeMap(event.getParameters()))
					));
				}
			}
		}
		if (out.isEmpty()) {
			// Fallback: high-level GlobalEvent API
			for (GlobalEvent ge : document.getGlobalEvents()) {
				if (ge == null) continue;
				String typeName = ge.getType() != null ? ge.getType().name() : "SPECIAL";
				String id = PlaybackEngine.syntheticGlobalId(ge.getTimeSeconds(), typeName, ge.getName(), index++);
				out.add(new CompiledGlobalEvent(
					 id,
					 ge.getTimeSeconds(),
					 GlobalEventPayloadCodec.decode(Map.of("type", typeName, "name", ge.getName()))
				));
			}
		}
		out.sort(Comparator
			.comparingDouble(CompiledGlobalEvent::timeSeconds)
			.thenComparing(CompiledGlobalEvent::id));
		return List.copyOf(out);
	}

	private static CompiledAudioReference compileAudio(Timeline document) {
		Object pathRaw = document.getMetadata("audioPath");
		String path = pathRaw != null ? String.valueOf(pathRaw).trim() : "";
		boolean present = !path.isBlank();
		boolean exists = false;
		if (present) {
			try {
				exists = Files.isRegularFile(Path.of(path));
			} catch (RuntimeException ignored) {
				exists = false;
			}
		}
		Object assetRaw = document.getMetadata("audioAssetId");
		String assetId = assetRaw != null ? String.valueOf(assetRaw).trim() : null;
		if (assetId != null && assetId.isBlank()) {
			assetId = null;
		}
		double duration = Math.max(0, document.getDurationSeconds());
		return new CompiledAudioReference(path, present, exists, duration, assetId);
	}

	private static TimelineAnimationEvent copyEvent(TimelineAnimationEvent event) {
		// Strong-type validate via StageEventPayload, then freeze parameter map.
		StageEventPayload payload = event.getPayload();
		if (!Double.isFinite(payload.durationSeconds())) {
			throw new TimelineCompilationException(
				"Non-finite duration for event " + event.getEventId() + ": " + payload.durationSeconds());
		}
		return new TimelineAnimationEvent(
			event.getEventId(),
			event.getTimeSeconds(),
			payload.durationSeconds(),
			payload.animationType(),
			payload.targetObject(),
			payload.energy(),
			freezeMap(payload.toParameterMap())
		);
	}

	private static Map<String, Object> freezeMap(Map<String, Object> source) {
		if (source == null || source.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> frozen = new LinkedHashMap<>();
		for (var entry : source.entrySet()) {
			frozen.put(entry.getKey(), freezeValue(entry.getValue()));
		}
		return Map.copyOf(frozen);
	}

	private static Object freezeValue(Object value) {
		if (value == null || value instanceof String || value instanceof Number
			|| value instanceof Boolean || value instanceof Character || value instanceof Enum<?>) {
			return value;
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> frozen = new LinkedHashMap<>();
			for (var entry : map.entrySet()) {
				frozen.put(String.valueOf(entry.getKey()), freezeValue(entry.getValue()));
			}
			return Map.copyOf(frozen);
		}
		if (value instanceof Iterable<?> iterable) {
			List<Object> frozen = new ArrayList<>();
			for (Object item : iterable) {
				frozen.add(freezeValue(item));
			}
			return List.copyOf(frozen);
		}
		if (value.getClass().isArray()) {
			List<Object> frozen = new ArrayList<>(Array.getLength(value));
			for (int i = 0; i < Array.getLength(value); i++) {
				frozen.add(freezeValue(Array.get(value, i)));
			}
			return List.copyOf(frozen);
		}
		throw new TimelineCompilationException(
			"Unsupported mutable parameter type: " + value.getClass().getName());
	}

	private static boolean shouldRestoreWorldMutations(Timeline timeline) {
		Object raw = timeline.getMetadata("timelineActionRollbackMode");
		if (raw == null) {
			return true;
		}
		String mode = String.valueOf(raw).trim().toLowerCase(java.util.Locale.ROOT);
		return !"persistent".equals(mode) && !"performance".equals(mode);
	}
}
