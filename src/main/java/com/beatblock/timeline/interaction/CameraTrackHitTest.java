package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.editor.TimelineViewState;

/**
 * 摄像机轨：片段左右边缘命中（用于拖拽调整长短）。
 */
public final class CameraTrackHitTest {

	public record EdgeHit(String clipId, boolean leftEdge) {}

	private CameraTrackHitTest() {}

	/**
	 * @param mouseX mouseY 屏幕坐标；rowScreenY/rowHeight 为摄像机行；contentLeft 为时间轴内容区左缘
	 */
	public static EdgeHit hitClipEdge(
		Timeline timeline,
		float mouseX,
		float mouseY,
		float rowScreenY,
		float rowHeight,
		float contentLeft,
		float contentWidth,
		TimelineViewState view,
		float edgePixels
	) {
		if (timeline == null || view == null) return null;
		if (mouseY < rowScreenY || mouseY > rowScreenY + rowHeight) return null;
		if (mouseX < contentLeft || mouseX > contentLeft + contentWidth) return null;
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		if (cam == null) return null;

		EdgeHit best = null;
		float bestDist = Float.MAX_VALUE;
		for (Clip clip : cam.getClips()) {
			if (clip == null) continue;
			if (CameraTrackFactory.findSegmentHeadEvent(clip) == null) continue;
			double cs = clip.getStartTimeSeconds();
			double ce = clip.getEndTimeSeconds();
			float x0 = contentLeft + view.timeToScreen(cs);
			float x1 = contentLeft + view.timeToScreen(ce);
			if (x1 < x0 + 4f) x1 = x0 + 4f;
			float dl = Math.abs(mouseX - x0);
			float dr = Math.abs(mouseX - x1);
			if (dl <= edgePixels && dl < bestDist) {
				best = new EdgeHit(clip.getId(), true);
				bestDist = dl;
			}
			if (dr <= edgePixels && dr < bestDist) {
				best = new EdgeHit(clip.getId(), false);
				bestDist = dr;
			}
		}
		return best;
	}
}
