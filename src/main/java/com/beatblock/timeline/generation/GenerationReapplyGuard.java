package com.beatblock.timeline.generation;

import com.beatblock.automap.choreography.ChoreographyCompileOptions;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineClipOrigin;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.binding.AnimationBindingEngine;
import com.beatblock.timeline.rendering.TimelineTrackMeta;

/**
 * 判断 Binding Map / Auto Map 等生成动作是否会覆盖已有自动生成内容，从而需要二次确认。
 */
public final class GenerationReapplyGuard {

	public enum Kind {
		BINDING_MAP_BLOCK,
		BINDING_MAP_AUTO,
		AUTO_MAP_TOOLBAR,
		SMART_AUTO_MAP
	}

	private GenerationReapplyGuard() {}

	public static boolean requiresConfirmation(Timeline timeline, Kind kind) {
		return affectedEventCount(timeline, kind) > 0;
	}

	public static int affectedEventCount(Timeline timeline, Kind kind) {
		if (timeline == null || kind == null) {
			return 0;
		}
		return switch (kind) {
			case BINDING_MAP_BLOCK -> AnimationBindingEngine.countRemovableGeneratedEvents(
				timeline, TimelineTrackMeta.ROW_ANIM_BLOCK);
			case BINDING_MAP_AUTO -> AnimationBindingEngine.countRemovableGeneratedEvents(
				timeline, TimelineTrackMeta.ROW_ANIM_AUTO);
			case AUTO_MAP_TOOLBAR -> countReplaceableEvents(
				timeline, Timeline.TRACK_ID_ANIMATION_AUTO, ContentReplacePolicy.replaceGenerated());
			case SMART_AUTO_MAP -> countSmartAutoMapReplaceableEvents(timeline);
		};
	}

	public static String messageKey(Kind kind) {
		return switch (kind) {
			case BINDING_MAP_BLOCK -> "beatblock.generation.reapply.binding_map_block";
			case BINDING_MAP_AUTO -> "beatblock.generation.reapply.binding_map_auto";
			case AUTO_MAP_TOOLBAR -> "beatblock.generation.reapply.auto_map";
			case SMART_AUTO_MAP -> "beatblock.generation.reapply.smart_auto_map";
		};
	}

	private static int countSmartAutoMapReplaceableEvents(Timeline timeline) {
		var options = ChoreographyCompileOptions.smartAutoMap();
		return countReplaceableEvents(timeline, Timeline.TRACK_ID_ANIMATION_AUTO, options.animationMode())
			+ countReplaceableEvents(timeline, Timeline.TRACK_ID_CAMERA, options.cameraMode())
			+ countReplaceableEvents(timeline, Timeline.TRACK_ID_GLOBAL, options.vfxMode());
	}

	private static int countReplaceableEvents(
		Timeline timeline,
		String trackId,
		ContentReplacePolicy policy
	) {
		if (timeline == null || trackId == null || policy == null) {
			return 0;
		}
		Track track = timeline.getTrack(trackId);
		if (track == null) {
			return 0;
		}
		int count = 0;
		for (Clip clip : track.getClips()) {
			if (clip == null || !TimelineClipOrigin.shouldRemove(clip, trackId, policy)) {
				continue;
			}
			count += clip.getEvents().size();
		}
		return count;
	}
}
