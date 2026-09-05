package com.beatblock.engine.layer;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * BuildLayer ↔ Timeline clip binding helpers shared by delete/cut/load paths.
 */
public final class BuildLayerBindingSupport {

	public record BindingSnapshot(
		@NonNull String layerId,
		@Nullable String boundClipId,
		@NonNull LayerVisibilityState state
	) {}

	private BuildLayerBindingSupport() {}

	public static boolean isLayerBindingEvent(@Nullable TimelineEvent event, @Nullable BuildLayer layer) {
		if (event == null || layer == null) return false;
		Object layerId = event.getParameter("layerId");
		return layer.getId().equals(layerId);
	}

	/**
	 * If {@code event} is the binding event for the layer currently bound to {@code clipId},
	 * unbinds the layer and returns a snapshot for undo. Otherwise returns null.
	 */
	public static @Nullable BindingSnapshot unbindIfBindingEvent(
		@Nullable BuildLayerManager manager,
		@Nullable String clipId,
		@Nullable TimelineEvent event
	) {
		if (manager == null || clipId == null || clipId.isBlank() || event == null) {
			return null;
		}
		BuildLayer layer = manager.getByClipId(clipId);
		if (!isLayerBindingEvent(event, layer)) {
			return null;
		}
		BindingSnapshot snapshot = new BindingSnapshot(layer.getId(), layer.getBoundClipId(), layer.getState());
		manager.unbindFromClip(layer);
		return snapshot;
	}

	public static void restoreBinding(
		@Nullable BuildLayerManager manager,
		@Nullable BindingSnapshot snapshot
	) {
		if (manager == null || snapshot == null) return;
		BuildLayer layer = manager.get(snapshot.layerId());
		if (layer == null) return;
		manager.restoreBinding(layer, snapshot.boundClipId(), snapshot.state());
	}

	/**
	 * Clears dangling bindings after load:
	 * <ul>
	 *   <li>{@code BOUND_TO_TRACK} with missing clip (or no timeline to verify) → unbind</li>
	 *   <li>non-bound state with leftover {@code boundClipId} → clear only when a timeline is present
	 *       (layer-only loads may still round-trip orphan clip ids until timeline reconcile)</li>
	 * </ul>
	 *
	 * @return number of layers adjusted
	 */
	public static int reconcileBindings(
		@Nullable BuildLayerManager manager,
		@Nullable Timeline timeline
	) {
		if (manager == null) return 0;
		int adjusted = 0;
		for (BuildLayer layer : manager.getAll()) {
			if (layer == null) continue;
			String clipId = layer.getBoundClipId();
			if (layer.getState() == LayerVisibilityState.BOUND_TO_TRACK) {
				if (timeline == null || clipId == null || clipId.isBlank() || !clipExists(timeline, clipId)) {
					manager.unbindFromClip(layer);
					adjusted++;
				}
			} else if (timeline != null && clipId != null && !clipId.isBlank()) {
				layer.setBoundClipId(null);
				adjusted++;
			}
		}
		return adjusted;
	}

	private static boolean clipExists(Timeline timeline, String clipId) {
		for (Track track : timeline.getTracks()) {
			if (track == null) continue;
			Clip clip = track.getClip(clipId);
			if (clip != null) return true;
		}
		return false;
	}
}
