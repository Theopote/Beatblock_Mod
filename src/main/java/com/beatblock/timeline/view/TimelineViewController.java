package com.beatblock.timeline.view;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;
import com.beatblock.timeline.rendering.TimelineFrameTrackSnapshot;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineRenderer;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.timeline.rendering.TimelineUiStateStore;
import com.beatblock.timeline.rendering.TrackDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 时间线视图域：拥有缩放/滚动、轨道显示状态、帧布局和只读轨道快照。
 */
public final class TimelineViewController {

	private final Timeline timeline;
	private final TimelineViewState viewState;
	private final TimelineToolbarState toolbarState = new TimelineToolbarState();
	private final TimelineTrackListState trackListState = new TimelineTrackListState();
	private final TimelineUiStateStore uiStateStore = new TimelineUiStateStore();
	private final TimelineLayout frameLayout = new TimelineLayout();
	private TimelineFrameTrackSnapshot frameTrackSnapshot = TimelineFrameTrackSnapshot.empty();
	private boolean frameLayoutPrepared;
	private boolean trackAreaContextAttached;
	private float dividerScreenX;
	private float dividerTopScreenY;
	private float dividerContentBottomScreenY;

	public TimelineViewController(Timeline timeline, TimelineViewState viewState) {
		this.timeline = timeline;
		this.viewState = viewState;
		uiStateStore.loadTrackListState(timeline, trackListState);
	}

	public TimelineViewState viewState() { return viewState; }
	public TimelineToolbarState toolbarState() { return toolbarState; }
	public TimelineTrackListState trackListState() { return trackListState; }
	public TimelineFrameTrackSnapshot frameTrackSnapshot() { return frameTrackSnapshot; }
	public float dividerScreenX() { return dividerScreenX; }
	public float dividerTopScreenY() { return dividerTopScreenY; }
	public float dividerContentBottomScreenY() { return dividerContentBottomScreenY; }

	public void beginFrame(TimelineRenderer renderer) {
		frameLayout.beginFrame(trackListState.getTrackHeaderWidth());
		frameLayoutPrepared = true;
		trackAreaContextAttached = false;
		renderer.prepareFrame(timeline);
		frameTrackSnapshot = TimelineFrameTrackSnapshot.build(timeline, frameTrackSnapshot);
	}

	public TimelineLayout frameLayout(TimelineRenderer renderer) {
		if (!frameLayoutPrepared) beginFrame(renderer);
		return frameLayout;
	}

	public TimelineLayout trackAreaLayout(TimelineRenderer renderer) {
		TimelineLayout layout = frameLayout(renderer);
		if (!trackAreaContextAttached) {
			attachTrackAreaContext(layout);
			trackAreaContextAttached = true;
		}
		return layout;
	}

	public void updateRulerDivider(TimelineLayout layout) {
		dividerScreenX = layout.contentLeft;
		dividerTopScreenY = layout.rulerTop;
	}

	public void updateTrackAreaDivider(TimelineLayout layout) {
		dividerScreenX = layout.contentLeft;
		dividerContentBottomScreenY = layout.contentTop + layout.contentHeight;
	}

	public void finishTrackAreaFrame() {
		syncBuildLayerTrackNamesFromUi();
		uiStateStore.syncAndFlush(timeline, trackListState);
	}

	private void attachTrackAreaContext(TimelineLayout layout) {
		List<TrackDefinition> audioDefs = frameTrackSnapshot.audioSubTracks();
		List<TrackDefinition> controlDefs = frameTrackSnapshot.animationSubTracks();
		List<TrackDefinition> buildLayerDefs = frameTrackSnapshot.buildLayerTracks();
		layout.setActiveAudioSubRowCount(audioDefs.size());
		layout.setActiveAnimationSubRowCount(controlDefs.size());
		layout.setActiveBuildLayerRowCount(buildLayerDefs.size());
		layout.setCustomRowOrder(buildFeaturePairedRowOrder(audioDefs, controlDefs, buildLayerDefs));
		layout.setCustomRowParents(buildCustomRowParents(audioDefs, controlDefs));
		layout.attachTrackAreaContext(trackListState);
	}

	private static List<Integer> buildFeaturePairedRowOrder(
		List<TrackDefinition> audioDefs,
		List<TrackDefinition> controlDefs,
		List<TrackDefinition> buildLayerDefs
	) {
		List<Integer> ordered = new ArrayList<>(TimelineLayout.CONTENT_ROW_COUNT);
		Set<Integer> addedRows = new HashSet<>();
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_AUDIO_GROUP);
		Map<String, Integer> audioFeatureRows = new HashMap<>();
		for (int slot = 0; slot < audioDefs.size() && slot < TimelineTrackMeta.MAX_AUDIO_SUB_ROWS; slot++) {
			TrackDefinition td = audioDefs.get(slot);
			int row = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			if (td.getVisualType() == TrackDefinition.VisualType.IMPULSE) audioFeatureRows.put(td.getKey(), row);
			else addRow(ordered, addedRows, row);
		}
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_ANIMATION_GROUP);
		Map<String, Integer> controlFeatureRows = new HashMap<>();
		for (int slot = 0; slot < controlDefs.size() && slot < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; slot++) {
			String featureKey = Timeline.blockAnimationFeatureKeyFromTrackId(controlDefs.get(slot).getKey());
			if (featureKey != null && !featureKey.isBlank()) {
				controlFeatureRows.put(featureKey, TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot);
			}
		}
		for (int slot = 0; slot < audioDefs.size() && slot < TimelineTrackMeta.MAX_AUDIO_SUB_ROWS; slot++) {
			TrackDefinition td = audioDefs.get(slot);
			if (td.getVisualType() != TrackDefinition.VisualType.IMPULSE) continue;
			Integer audioRow = audioFeatureRows.get(td.getKey());
			Integer controlRow = controlFeatureRows.get(td.getKey());
			if (audioRow != null) addRow(ordered, addedRows, audioRow);
			if (controlRow != null) addRow(ordered, addedRows, controlRow);
		}
		for (int slot = 0; slot < controlDefs.size() && slot < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; slot++) {
			addRow(ordered, addedRows, TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot);
		}
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_ACTION_GROUP);
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_ANIM_BLOCK);
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_CAMERA);
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_ANIM_AUTO);
		for (int slot = 0; slot < buildLayerDefs.size() && slot < TimelineTrackMeta.MAX_BUILD_LAYER_ROWS; slot++) {
			addRow(ordered, addedRows, TimelineTrackMeta.ROW_BUILD_LAYER_START + slot);
		}
		addRow(ordered, addedRows, TimelineTrackMeta.ROW_GLOBAL_EVENT);
		for (int row = 0; row < TimelineLayout.CONTENT_ROW_COUNT; row++) addRow(ordered, addedRows, row);
		return ordered;
	}

	private static Map<Integer, Integer> buildCustomRowParents(
		List<TrackDefinition> audioDefs,
		List<TrackDefinition> controlDefs
	) {
		Map<Integer, Integer> parents = new HashMap<>();
		for (int slot = 0; slot < audioDefs.size() && slot < TimelineTrackMeta.MAX_AUDIO_SUB_ROWS; slot++) {
			int row = TimelineTrackMeta.ROW_AUDIO_SUBS_START + slot;
			parents.put(row, audioDefs.get(slot).getVisualType() == TrackDefinition.VisualType.WAVEFORM
				? TimelineTrackMeta.ROW_AUDIO_GROUP : TimelineTrackMeta.ROW_ANIMATION_GROUP);
		}
		for (int slot = 0; slot < controlDefs.size() && slot < TimelineTrackMeta.MAX_ANIMATION_SUB_ROWS; slot++) {
			parents.put(TimelineTrackMeta.ROW_ANIM_FEATURES_START + slot, TimelineTrackMeta.ROW_ANIMATION_GROUP);
		}
		parents.put(TimelineTrackMeta.ROW_ACTION_GROUP, TimelineTrackMeta.NO_PARENT);
		parents.put(TimelineTrackMeta.ROW_ANIM_BLOCK, TimelineTrackMeta.ROW_ACTION_GROUP);
		parents.put(TimelineTrackMeta.ROW_CAMERA, TimelineTrackMeta.ROW_ACTION_GROUP);
		parents.put(TimelineTrackMeta.ROW_ANIM_AUTO, TimelineTrackMeta.ROW_ACTION_GROUP);
		for (int slot = 0; slot < TimelineTrackMeta.MAX_BUILD_LAYER_ROWS; slot++) {
			parents.put(TimelineTrackMeta.ROW_BUILD_LAYER_START + slot, TimelineTrackMeta.ROW_ACTION_GROUP);
		}
		return parents;
	}

	private static void addRow(List<Integer> ordered, Set<Integer> addedRows, int row) {
		if (row >= 0 && row < TimelineLayout.CONTENT_ROW_COUNT && addedRows.add(row)) ordered.add(row);
	}

	private void syncBuildLayerTrackNamesFromUi() {
		var tracks = BuildLayerTrackSupport.listTracks(timeline);
		for (int i = 0; i < tracks.size(); i++) {
			String custom = trackListState.getCustomNameOrNull(BuildLayerTrackSupport.rowForSlot(i));
			if (custom != null) tracks.get(i).setName(custom);
		}
	}
}
