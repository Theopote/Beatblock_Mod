package com.beatblock.timeline.generation;

import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Snapshot of Timeline tracks + choreography metadata + BuildLayer bind state
 * for one Smart AutoMap undo transaction.
 */
public final class GenerationDocumentSnapshot {

	private static final String[] CORE_TRACK_IDS = {
		Timeline.TRACK_ID_ANIMATION_AUTO,
		Timeline.TRACK_ID_ANIMATION_BLOCK,
		Timeline.TRACK_ID_CAMERA,
		Timeline.TRACK_ID_GLOBAL
	};

	private record ClipSnapshot(
		String clipId,
		double startTimeSeconds,
		double endTimeSeconds,
		List<TimelineEvent> events
	) {}

	private record LayerBindSnapshot(
		String layerId,
		@Nullable String boundClipId,
		LayerVisibilityState state
	) {}

	private final Map<String, List<ClipSnapshot>> clipsByTrack;
	private final @Nullable Object choreographyPlan;
	private final @Nullable Object autoMapConfig;
	private final List<LayerBindSnapshot> layerBinds;

	private GenerationDocumentSnapshot(
		Map<String, List<ClipSnapshot>> clipsByTrack,
		@Nullable Object choreographyPlan,
		@Nullable Object autoMapConfig,
		List<LayerBindSnapshot> layerBinds
	) {
		this.clipsByTrack = clipsByTrack;
		this.choreographyPlan = choreographyPlan;
		this.autoMapConfig = autoMapConfig;
		this.layerBinds = List.copyOf(layerBinds != null ? layerBinds : List.of());
	}

	public static GenerationDocumentSnapshot capture(
		@Nullable Timeline timeline,
		@Nullable BuildLayerManager layerManager
	) {
		Map<String, List<ClipSnapshot>> clipsByTrack = new LinkedHashMap<>();
		if (timeline == null) {
			return new GenerationDocumentSnapshot(clipsByTrack, null, null, List.of());
		}
		for (String trackId : trackedTrackIds(timeline)) {
			clipsByTrack.put(trackId, captureTrack(timeline.getTrack(trackId)));
		}
		List<LayerBindSnapshot> binds = new ArrayList<>();
		if (layerManager != null) {
			for (BuildLayer layer : layerManager.getAll()) {
				if (layer == null) continue;
				binds.add(new LayerBindSnapshot(
					layer.getId(),
					layer.getBoundClipId(),
					layer.getState()
				));
			}
		}
		return new GenerationDocumentSnapshot(
			clipsByTrack,
			timeline.getMetadata(ChoreographyPlanStore.KEY_PLAN),
			timeline.getMetadata(ChoreographyPlanStore.KEY_CONFIG),
			binds
		);
	}

	public void restore(@Nullable Timeline timeline, @Nullable BuildLayerManager layerManager) {
		if (timeline == null) {
			return;
		}
		Set<String> trackIds = new LinkedHashSet<>(trackedTrackIds(timeline));
		trackIds.addAll(clipsByTrack.keySet());
		for (String trackId : trackIds) {
			Track track = timeline.getTrack(trackId);
			if (track == null) {
				continue;
			}
			for (Clip existing : new ArrayList<>(track.getClips())) {
				track.removeClip(existing.getId());
			}
			for (ClipSnapshot snapshot : clipsByTrack.getOrDefault(trackId, List.of())) {
				Clip clip = new Clip(snapshot.clipId(), snapshot.startTimeSeconds(), snapshot.endTimeSeconds());
				for (TimelineEvent event : snapshot.events()) {
					clip.addEvent(copyEvent(event));
				}
				track.addClip(clip);
			}
			timeline.markAnimationEventsDirty(trackId);
		}
		timeline.setMetadata(ChoreographyPlanStore.KEY_PLAN, choreographyPlan);
		timeline.setMetadata(ChoreographyPlanStore.KEY_CONFIG, autoMapConfig);

		if (layerManager == null) {
			return;
		}
		for (LayerBindSnapshot bind : layerBinds) {
			BuildLayer layer = layerManager.get(bind.layerId());
			if (layer == null) {
				continue;
			}
			layerManager.restoreBinding(layer, bind.boundClipId(), bind.state());
		}
	}

	private static List<String> trackedTrackIds(Timeline timeline) {
		List<String> ids = new ArrayList<>(List.of(CORE_TRACK_IDS));
		for (Track track : BuildLayerTrackSupport.listTracks(timeline)) {
			if (track != null && track.getId() != null && !track.getId().isBlank()) {
				ids.add(track.getId());
			}
		}
		return ids;
	}

	private static List<ClipSnapshot> captureTrack(@Nullable Track track) {
		List<ClipSnapshot> clips = new ArrayList<>();
		if (track == null) {
			return clips;
		}
		for (Clip clip : track.getClips()) {
			List<TimelineEvent> events = new ArrayList<>();
			for (TimelineEvent event : clip.getEvents()) {
				events.add(copyEvent(event));
			}
			clips.add(new ClipSnapshot(
				clip.getId(),
				clip.getStartTimeSeconds(),
				clip.getEndTimeSeconds(),
				events
			));
		}
		return clips;
	}

	private static TimelineEvent copyEvent(TimelineEvent source) {
		return new TimelineEvent(
			source.getId(),
			source.getTimeSeconds(),
			source.getType(),
			new HashMap<>(source.getParameters())
		);
	}
}
