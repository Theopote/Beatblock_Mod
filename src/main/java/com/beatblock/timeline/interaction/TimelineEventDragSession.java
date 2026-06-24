package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.InteractionMode;

/** 事件拖动开始时的 timeSeconds 快照。 */
public final class TimelineEventDragSession {

	private double initialTimeSeconds;

	public double initialTimeSeconds() {
		return initialTimeSeconds;
	}

	public static TimelineEventDragSession begin(
		Timeline timeline,
		HitResult hit,
		InteractionState interactionState,
		float mx,
		float my
	) {
		TimelineEventDragSession session = new TimelineEventDragSession();
		interactionState.setMode(InteractionMode.DRAG_EVENT);
		interactionState.setMouseStart(mx, my);
		interactionState.setActiveEventId(hit.getEventId());
		interactionState.setActiveClipId(hit.getClipId());
		interactionState.setActiveTrackId(hit.getTrackId());
		if (hit.getEventId() != null) {
			TimelineEventRef dragRef = TimelineEventRefs.find(timeline, hit.getEventId());
			if (dragRef != null && dragRef.event() != null) {
				session.initialTimeSeconds = dragRef.event().getTimeSeconds();
			}
		}
		return session;
	}

	public void clear() {
		initialTimeSeconds = 0.0;
	}
}
