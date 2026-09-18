package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editing.TimelineEventMovePolicy;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.util.SnapSystem;
import com.beatblock.timeline.util.TimeUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * 拖拽逻辑：事件时间更新、可选吸附。
 */
public final class DragController {

	private DragController() {}

	/**
	 * 将指定事件的时间设为 newTimeSeconds，吸附后按 {@link TimelineEventMovePolicy} 夹取。
	 * 不自动扩展所属 Clip。
	 */
	public static void dragEvent(Timeline timeline, String trackId, String clipId, String eventId,
			double newTimeSeconds,
			TimelineToolbarState toolbarState, TimelineViewState viewState,
			InteractionState interactionState) {
		Track track = timeline != null ? timeline.getTrack(trackId) : null;
		if (track == null) return;
		Clip clip = track.getClip(clipId);
		if (clip == null) return;
		TimelineEvent event = clip.getEvent(eventId);
		if (event == null) return;

		TimelineEventMovePolicy.MoveBounds bounds = TimelineEventMovePolicy.boundsFor(clip, event);
		if (bounds.isFixed()) {
			return;
		}

		double t = computeEventDragTime(
			newTimeSeconds,
			eventId,
			bounds.minTimeSeconds(),
			bounds.maxTimeSeconds(),
			timeline,
			toolbarState,
			viewState,
			interactionState
		);
		if (Double.isNaN(t)) return;

		event.setTimeSeconds(t);
		if (isAnimationTrack(trackId)) {
			timeline.markAnimationEventsDirty(trackId);
		}
	}

	/**
	 * 多选组拖：主事件吸附后，其余选中事件按相同 delta 移动并各自夹到所属 Clip 范围。
	 */
	public static void dragEventGroup(
		Timeline timeline,
		TimelineEventDragSession session,
		double primaryNewTimeSeconds,
		TimelineToolbarState toolbarState,
		TimelineViewState viewState,
		InteractionState interactionState
	) {
		if (timeline == null || session == null || session.primaryEventId() == null) {
			return;
		}
		TimelineEventRef primaryRef = TimelineEventRefs.find(timeline, session.primaryEventId());
		if (primaryRef == null || primaryRef.event() == null || primaryRef.clip() == null) {
			return;
		}
		TimelineEventMovePolicy.MoveBounds primaryBounds =
			TimelineEventMovePolicy.boundsFor(primaryRef.clip(), primaryRef.event());
		if (primaryBounds.isFixed()) {
			return;
		}

		double snappedPrimary = computeEventDragTime(
			primaryNewTimeSeconds,
			session.primaryEventId(),
			primaryBounds.minTimeSeconds(),
			primaryBounds.maxTimeSeconds(),
			timeline,
			toolbarState,
			viewState,
			interactionState,
			session.memberEventIds()
		);
		if (Double.isNaN(snappedPrimary)) {
			return;
		}

		TimelineEventDragSession.Member primaryMember = session.member(session.primaryEventId());
		double primaryInitial = primaryMember != null
			? primaryMember.initialTimeSeconds()
			: primaryRef.event().getTimeSeconds();
		double delta = snappedPrimary - primaryInitial;

		Set<String> dirtyTracks = new HashSet<>();
		for (TimelineEventDragSession.Member member : session.members()) {
			TimelineEventRef ref = TimelineEventRefs.find(timeline, member.eventId());
			if (ref == null || ref.event() == null || ref.clip() == null) {
				continue;
			}
			TimelineEventMovePolicy.MoveBounds bounds = TimelineEventMovePolicy.boundsFor(ref.clip(), ref.event());
			if (bounds.isFixed()) {
				continue;
			}
			double target = bounds.clamp(member.initialTimeSeconds() + delta);
			ref.event().setTimeSeconds(target);
			if (isAnimationTrack(member.trackId())) {
				dirtyTracks.add(member.trackId());
			}
		}
		for (String trackId : dirtyTracks) {
			timeline.markAnimationEventsDirty(trackId);
		}
	}

	private static boolean isAnimationTrack(String trackId) {
		return Timeline.isAnimationEventsTrackId(trackId);
	}

	/**
	 * 计算吸附后的目标时间并夹到 [0, duration]，不修改时间线。
	 * 事件拖动请优先走 {@link #dragEvent}（使用 {@link TimelineEventMovePolicy}）。
	 */
	public static double computeEventDragTime(
		double newTimeSeconds,
		String eventId,
		double duration,
		Timeline timeline,
		TimelineToolbarState toolbarState,
		TimelineViewState viewState,
		InteractionState interactionState
	) {
		return computeEventDragTime(
			newTimeSeconds,
			eventId,
			0.0,
			duration > 0 ? duration : Double.MAX_VALUE,
			timeline,
			toolbarState,
			viewState,
			interactionState
		);
	}

	/**
	 * 计算吸附后的目标时间并夹到给定范围，不修改时间线。
	 */
	public static double computeEventDragTime(
		double newTimeSeconds,
		String eventId,
		double minTimeSeconds,
		double maxTimeSeconds,
		Timeline timeline,
		TimelineToolbarState toolbarState,
		TimelineViewState viewState,
		InteractionState interactionState
	) {
		Set<String> excludeEventIds = eventId != null && !eventId.isBlank() ? Set.of(eventId) : Set.of();
		return computeEventDragTime(
			newTimeSeconds, eventId, minTimeSeconds, maxTimeSeconds,
			timeline, toolbarState, viewState, interactionState, excludeEventIds);
	}

	public static double computeEventDragTime(
		double newTimeSeconds,
		String eventId,
		double minTimeSeconds,
		double maxTimeSeconds,
		Timeline timeline,
		TimelineToolbarState toolbarState,
		TimelineViewState viewState,
		InteractionState interactionState,
		Set<String> magnetExcludeEventIds
	) {
		if (timeline == null) return Double.NaN;

		SnapSystem.SnapResult snapped = applySnapWithGuides(
			newTimeSeconds, magnetExcludeEventIds, timeline, toolbarState, viewState);
		if (interactionState != null) {
			interactionState.setAlignmentGuideTimes(snapped.guideTimes());
		}
		double lo = Math.min(minTimeSeconds, maxTimeSeconds);
		double hi = Math.max(minTimeSeconds, maxTimeSeconds);
		return Math.max(lo, Math.min(snapped.timeSeconds(), hi));
	}

	/**
	 * Drag a clip by mouse delta. Start is clamped to {@code >= 0}; length is preserved.
	 * May extend {@link Timeline#getDurationSeconds()} so the moved clip remains in-document.
	 *
	 * @return applied clip start time
	 */
	public static double dragClip(Timeline timeline, String trackId, String clipId,
			double mouseTimeSeconds, double dragInitialMouseTime,
			double dragInitialClipStart, double clipDuration,
			TimelineToolbarState toolbarState, TimelineViewState viewState,
			InteractionState interactionState) {
		if (timeline == null || trackId == null || clipId == null) return dragInitialClipStart;
		Track track = timeline.getTrack(trackId);
		if (track == null) return dragInitialClipStart;
		Clip clip = track.getClip(clipId);
		if (clip == null) return dragInitialClipStart;

		double rawNewStart = dragInitialClipStart + (mouseTimeSeconds - dragInitialMouseTime);
		SnapSystem.SnapResult snapped = applySnapWithGuides(rawNewStart, (String) null, timeline, toolbarState, viewState);
		if (interactionState != null) {
			interactionState.setAlignmentGuideTimes(snapped.guideTimes());
		}
		double clampedStart = Math.max(0.0, snapped.timeSeconds());

		clip.setStartTimeSeconds(clampedStart);
		clip.setEndTimeSeconds(clampedStart + clipDuration);
		timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), clampedStart + clipDuration));
		return clampedStart;
	}

	/** 供片段边缘拖拽等复用：与 {@link #dragEvent} 相同的吸附规则。 */
	public static double snapTime(double timeSeconds, String excludeEventId, Timeline timeline,
			TimelineToolbarState toolbarState, TimelineViewState viewState) {
		return snapTime(timeSeconds, excludeEventId, null, timeline, toolbarState, viewState, null);
	}

	public static double snapTime(double timeSeconds, String excludeEventId, Timeline timeline,
			TimelineToolbarState toolbarState, TimelineViewState viewState,
			InteractionState interactionState) {
		return snapTime(timeSeconds, excludeEventId, null, timeline, toolbarState, viewState, interactionState);
	}

	public static double snapTime(
		double timeSeconds,
		String excludeEventId,
		String excludeMarkerId,
		Timeline timeline,
		TimelineToolbarState toolbarState,
		TimelineViewState viewState,
		InteractionState interactionState
	) {
		SnapSystem.SnapResult result = applySnapWithGuides(
			timeSeconds, excludeEventId, excludeMarkerId, timeline, toolbarState, viewState);
		if (interactionState != null) {
			interactionState.setAlignmentGuideTimes(result.guideTimes());
		}
		return result.timeSeconds();
	}

	/**
	 * Marker 拖动：按工具栏 Snap 设置吸附，夹到 {@code [0, duration]}，并写入对齐参考线。
	 */
	public static double computeMarkerDragTime(
		double newTimeSeconds,
		String markerId,
		double duration,
		Timeline timeline,
		TimelineToolbarState toolbarState,
		TimelineViewState viewState,
		InteractionState interactionState
	) {
		if (timeline == null) {
			return Double.NaN;
		}
		double hi = duration > 0 ? duration : Double.MAX_VALUE;
		double snapped = snapTime(
			newTimeSeconds,
			null,
			markerId,
			timeline,
			toolbarState,
			viewState,
			interactionState
		);
		return Math.max(0.0, Math.min(snapped, hi));
	}

	private static SnapSystem.SnapResult applySnapWithGuides(double timeSeconds, String excludeEventId, Timeline timeline,
			TimelineToolbarState toolbarState, TimelineViewState viewState) {
		return applySnapWithGuides(timeSeconds, excludeEventId, null, timeline, toolbarState, viewState);
	}

	private static SnapSystem.SnapResult applySnapWithGuides(
		double timeSeconds,
		String excludeEventId,
		String excludeMarkerId,
		Timeline timeline,
		TimelineToolbarState toolbarState,
		TimelineViewState viewState
	) {
		Set<String> excludeEventIds = excludeEventId != null && !excludeEventId.isBlank()
			? Set.of(excludeEventId)
			: Set.of();
		return applySnapWithGuides(timeSeconds, excludeEventIds, excludeMarkerId, timeline, toolbarState, viewState);
	}

	private static SnapSystem.SnapResult applySnapWithGuides(
		double timeSeconds,
		Set<String> excludeEventIds,
		Timeline timeline,
		TimelineToolbarState toolbarState,
		TimelineViewState viewState
	) {
		return applySnapWithGuides(timeSeconds, excludeEventIds, null, timeline, toolbarState, viewState);
	}

	private static SnapSystem.SnapResult applySnapWithGuides(
		double timeSeconds,
		Set<String> excludeEventIds,
		String excludeMarkerId,
		Timeline timeline,
		TimelineToolbarState toolbarState,
		TimelineViewState viewState
	) {
		if (toolbarState == null) return SnapSystem.SnapResult.unchanged(timeSeconds);
		boolean grid = toolbarState.isSnapToGrid();
		boolean beat = toolbarState.isSnapToBeat();
		boolean magnet = toolbarState.isMagnetSnap();
		if (!grid && !beat && !magnet) return SnapSystem.SnapResult.unchanged(timeSeconds);

		double gridStep = 0;
		if (grid && viewState != null) {
			gridStep = TimeUtils.gridStep(
				viewState.getViewStartTimeSeconds(),
				viewState.getViewEndTimeSeconds(),
				viewState.getZoom());
		}
		return SnapSystem.snapWithGuides(
			timeSeconds, timeline, grid, gridStep, beat, timeline.getBpm(), magnet, excludeEventIds, excludeMarkerId);
	}
}
