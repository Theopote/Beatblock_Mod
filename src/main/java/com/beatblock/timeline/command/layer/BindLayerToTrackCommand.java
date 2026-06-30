package com.beatblock.timeline.command.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * 将 FREE_HIDDEN 图层绑定到指定建造图层轨道：创建 Clip + BUILD 事件，图层进入 BOUND_TO_TRACK。
 */
public final class BindLayerToTrackCommand implements com.beatblock.timeline.command.Command {

	public static final double DEFAULT_CLIP_DURATION_SECONDS = 2.0;

	private final Timeline timeline;
	private final BuildLayerManager layerManager;
	private final String layerId;
	private final String targetTrackId;
	private final double clipStartSeconds;
	private final double clipDurationSeconds;

	private @Nullable String createdClipId;
	private @Nullable String createdEventId;
	private @Nullable String previousBoundClipId;
	private @Nullable LayerVisibilityState previousState;

	public BindLayerToTrackCommand(
		Timeline timeline,
		BuildLayerManager layerManager,
		com.beatblock.timeline.command.CommandManager commandManager,
		String layerId,
		double clipStartSeconds,
		double clipDurationSeconds
	) {
		this(timeline, layerManager, layerId, null, clipStartSeconds, clipDurationSeconds);
	}

	public BindLayerToTrackCommand(
		Timeline timeline,
		BuildLayerManager layerManager,
		String layerId,
		@Nullable String targetTrackId,
		double clipStartSeconds,
		double clipDurationSeconds
	) {
		this.timeline = timeline;
		this.layerManager = layerManager;
		this.layerId = layerId;
		this.targetTrackId = targetTrackId;
		this.clipStartSeconds = Math.max(0, clipStartSeconds);
		this.clipDurationSeconds = clipDurationSeconds > 0 ? clipDurationSeconds : DEFAULT_CLIP_DURATION_SECONDS;
	}

	@Override
	public void execute() {
		BuildLayer layer = layerManager != null ? layerManager.get(layerId) : null;
		if (layer == null || !layer.canBindToTrack() || timeline == null) {
			return;
		}

		Track track = resolveTargetTrack();
		if (track == null) {
			return;
		}

		previousState = layer.getState();
		previousBoundClipId = layer.getBoundClipId();

		createdClipId = "clip_layer_" + UUID.randomUUID().toString().substring(0, 8);
		createdEventId = "evt_layer_" + UUID.randomUUID().toString().substring(0, 8);
		double clipEnd = clipStartSeconds + clipDurationSeconds;

		Clip clip = new Clip(createdClipId, clipStartSeconds, clipEnd);
		track.addClip(clip);

		Map<String, Object> params = new AnimationEventParams(
			TimelineAnimationActionMode.BUILD,
			"",
			layer.getStageObjectId(),
			1f,
			clipDurationSeconds,
			TimelineEventOrigin.MANUAL,
			Map.of(
				"layerId", layer.getId(),
				"buildMode", "WALL",
				"buildDissolve", "false",
				"layerBound", "true"
			)
		).toParameterMap();

		TimelineEvent event = new TimelineEvent(
			createdEventId,
			clipStartSeconds,
			EventType.ANIMATION,
			params
		);
		clip.addEvent(event);

		layerManager.bindToClip(layer, createdClipId);
		timeline.markAnimationEventsDirty(track.getId());
	}

	private @Nullable Track resolveTargetTrack() {
		if (timeline == null) {
			return null;
		}
		BuildLayerTrackSupport.normalizeLoadedTracks(timeline);
		if (targetTrackId != null && !targetTrackId.isBlank()) {
			Track explicit = timeline.getTrack(targetTrackId);
			if (explicit != null && BuildLayerTrackSupport.isBuildLayerTrack(explicit)) {
				return explicit;
			}
			return null;
		}
		return BuildLayerTrackSupport.ensureDefaultTrack(timeline);
	}

	@Override
	public void undo() {
		BuildLayer layer = layerManager != null ? layerManager.get(layerId) : null;
		if (timeline == null || createdClipId == null) {
			return;
		}

		Track track = findTrackContainingClip(createdClipId);
		if (track != null) {
			Clip clip = track.getClip(createdClipId);
			if (clip != null && createdEventId != null) {
				clip.removeEvent(createdEventId);
			}
			track.removeClip(createdClipId);
			timeline.markAnimationEventsDirty(track.getId());
		}

		if (layer != null) {
			layerManager.restoreBinding(layer, previousBoundClipId, previousState);
		}

		createdClipId = null;
		createdEventId = null;
	}

	private @Nullable Track findTrackContainingClip(String clipId) {
		for (Track candidate : BuildLayerTrackSupport.listTracks(timeline)) {
			if (candidate.getClip(clipId) != null) {
				return candidate;
			}
		}
		Track legacy = timeline.getTrack(BuildLayerTrackSupport.LEGACY_TRACK_ID);
		if (legacy != null && legacy.getClip(clipId) != null) {
			return legacy;
		}
		return null;
	}

	public @Nullable String getCreatedClipId() {
		return createdClipId;
	}

	public @Nullable String getTargetTrackId() {
		Track track = findTrackContainingClip(createdClipId != null ? createdClipId : "");
		return track != null ? track.getId() : targetTrackId;
	}
}
