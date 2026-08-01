package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.StageObject;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles an editable {@link Timeline} into an immutable {@link CompiledTimelineSnapshot}
 * (Phase B: stage + camera + build layers + audio + markers + validation report).
 */
public final class TimelineCompiler {

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
		if (document == null) {
			return CompiledTimelineSnapshot.empty();
		}

		TimelineValidationReport report = TimelineValidator.validate(document, engine, layerManager);
		if (report.hasFatalErrors()) {
			String message = report.diagnostics().stream()
				.filter(d -> d.severity() == TimelineDiagnosticSeverity.ERROR && d.isFatal())
				.map(TimelineDiagnostic::message)
				.findFirst()
				.orElse("Fatal timeline validation error");
			throw new TimelineCompilationException(message);
		}

		List<TimelineAnimationEvent> events = new ArrayList<>();
		for (TimelineAnimationEvent event : document.getStageEvents()) {
			events.add(copyEvent(event));
		}
		events.sort(Comparator
			.comparingDouble(TimelineAnimationEvent::getTimeSeconds)
			.thenComparing(TimelineAnimationEvent::getEventId));

		double bpm = document.getBpm() > 0 ? document.getBpm() : 120.0;
		double duration = Math.max(0, document.getDurationSeconds());

		return new CompiledTimelineSnapshot(
			events,
			compileStageEvents(events, engine),
			compileCameraTrack(document.getTrack(Timeline.TRACK_ID_CAMERA)),
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
	}

	private static List<CompiledStageEvent> compileStageEvents(
		List<TimelineAnimationEvent> events,
		@Nullable BlockAnimationEngine engine
	) {
		List<CompiledStageEvent> compiled = new ArrayList<>(events.size());
		for (TimelineAnimationEvent event : events) {
			var definition = engine != null
				? engine.getAnimationLibrary().get(event.getAnimationTypeId())
				: null;
			StageObject source = engine != null
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
			compiled.add(new CompiledStageEvent(event, definition, target));
		}
		return List.copyOf(compiled);
	}

	private static CompiledCameraTrack compileCameraTrack(@Nullable Track track) {
		if (track == null || !track.isEnabled()) {
			return new CompiledCameraTrack(List.of());
		}
		List<CompiledCameraTrack.CameraClip> clips = new ArrayList<>();
		for (var clip : track.getClips()) {
			List<CompiledCameraTrack.CameraEvent> cameraEvents = new ArrayList<>();
			for (var event : clip.getEvents()) {
				cameraEvents.add(new CompiledCameraTrack.CameraEvent(
					event.getId(),
					event.getTimeSeconds(),
					event.getType(),
					freezeMap(event.getParameters())
				));
			}
			cameraEvents.sort(Comparator.comparingDouble(CompiledCameraTrack.CameraEvent::timeSeconds)
				.thenComparing(CompiledCameraTrack.CameraEvent::id));
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
			StageObject stage = layer.getStageObject();
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
						 new GlobalEventPayload.Generic(typeName, name, freezeMap(event.getParameters()))
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
					 new GlobalEventPayload.Generic(typeName, ge.getName(), Map.of())
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
