package com.beatblock.timeline.command;

import com.beatblock.engine.layer.BuildLayerBindingSupport;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 删除事件：execute 从 Clip 移除事件，undo 加回。
 * <p>
 * When a BuildLayer binding event is removed, the layer is unbound on execute and
 * restored on undo (optional {@link BuildLayerManager}).
 */
public final class DeleteEventCommand implements Command {

	private final Timeline timeline;
	private final @Nullable BuildLayerManager layerManager;
	private final String trackId;
	private final String clipId;
	private final TimelineEvent event;
	private boolean done;
	private BuildLayerBindingSupport.@Nullable BindingSnapshot unboundLayer;

	public DeleteEventCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull TimelineEvent event
	) {
		this(timeline, null, trackId, clipId, event);
	}

	public DeleteEventCommand(
		@NonNull Timeline timeline,
		@Nullable BuildLayerManager layerManager,
		@NonNull String trackId,
		@NonNull String clipId,
		@NonNull TimelineEvent event
	) {
		this.timeline = timeline;
		this.layerManager = layerManager;
		this.trackId = trackId;
		this.clipId = clipId;
		this.event = event;
	}

	@Override
	public void execute() {
		if (timeline == null || event == null || done) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		unboundLayer = BuildLayerBindingSupport.unbindIfBindingEvent(layerManager, clipId, event);
		if (clip.removeEvent(event.getId())) {
			done = true;
			timeline.markAnimationEventsDirty(trackId);
		} else if (unboundLayer != null) {
			BuildLayerBindingSupport.restoreBinding(layerManager, unboundLayer);
			unboundLayer = null;
		}
	}

	@Override
	public void undo() {
		if (!done) return;
		Track track = timeline.getTrack(trackId);
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip != null) {
			clip.addEvent(event);
			timeline.markAnimationEventsDirty(trackId);
		}
		BuildLayerBindingSupport.restoreBinding(layerManager, unboundLayer);
		unboundLayer = null;
		done = false;
	}
}
