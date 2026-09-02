package com.beatblock.automap.choreography;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.generation.ContentReplacePolicy;

/** 将 {@link ContentReplacePolicy} 应用到 Timeline 轨道清理。 */
final class ChoreographyCompileApplicator {

	private ChoreographyCompileApplicator() {}

	static void applyAnimation(Timeline timeline, ContentReplacePolicy policy) {
		if (timeline == null || policy == null) return;
		if (policy instanceof ContentReplacePolicy.Append) return;
		if (policy instanceof ContentReplacePolicy.ReplaceAll) {
			timeline.clearAutoAnimationEvents();
			timeline.clearBlockAnimationEvents();
			return;
		}
		timeline.applyContentReplacePolicy(Timeline.TRACK_ID_ANIMATION_AUTO, policy);
	}

	static void applyCamera(Timeline timeline, ContentReplacePolicy policy) {
		if (timeline == null || policy == null) return;
		if (policy instanceof ContentReplacePolicy.Append) return;
		if (policy instanceof ContentReplacePolicy.ReplaceAll) {
			timeline.clearCameraKeyframes();
			return;
		}
		timeline.applyContentReplacePolicy(Timeline.TRACK_ID_CAMERA, policy);
	}

	static void applyVfx(Timeline timeline, ContentReplacePolicy policy) {
		if (timeline == null || policy == null) return;
		if (policy instanceof ContentReplacePolicy.Append) return;
		if (policy instanceof ContentReplacePolicy.ReplaceAll) {
			timeline.clearGlobalEvents();
			return;
		}
		timeline.applyContentReplacePolicy(Timeline.TRACK_ID_GLOBAL, policy);
	}
}
