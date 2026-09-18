package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editing.TimelineEventMovePolicy;
import com.beatblock.timeline.editor.HitResult;
import com.beatblock.timeline.editor.InteractionMode;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.rendering.TimelineTrackListState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 事件拖动开始时的 timeSeconds 快照；支持多选组拖。 */
public final class TimelineEventDragSession {

	public record Member(String trackId, String clipId, String eventId, double initialTimeSeconds) {}

	private String primaryEventId;
	private double initialTimeSeconds;
	private final Map<String, Member> members = new LinkedHashMap<>();

	public double initialTimeSeconds() {
		return initialTimeSeconds;
	}

	public String primaryEventId() {
		return primaryEventId;
	}

	public Collection<Member> members() {
		return List.copyOf(members.values());
	}

	public Set<String> memberEventIds() {
		return Set.copyOf(members.keySet());
	}

	public Member member(String eventId) {
		return members.get(eventId);
	}

	public static TimelineEventDragSession begin(
		Timeline timeline,
		HitResult hit,
		SelectionState selectionState,
		TimelineTrackListState trackListState,
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

		String activeEventId = hit.getEventId();
		session.primaryEventId = activeEventId;
		boolean groupDrag = selectionState != null
			&& activeEventId != null
			&& selectionState.isEventSelected(activeEventId)
			&& selectionState.getSelectedEvents().size() > 1;

		if (groupDrag) {
			for (String eventId : selectionState.getSelectedEvents()) {
				session.tryAddMember(timeline, trackListState, eventId);
			}
		} else if (activeEventId != null) {
			session.tryAddMember(timeline, trackListState, activeEventId);
		}

		Member primary = session.members.get(activeEventId);
		if (primary != null) {
			session.initialTimeSeconds = primary.initialTimeSeconds();
		} else if (activeEventId != null) {
			TimelineEventRef dragRef = TimelineEventRefs.find(timeline, activeEventId);
			if (dragRef != null && dragRef.event() != null) {
				session.initialTimeSeconds = dragRef.event().getTimeSeconds();
			}
		}
		return session;
	}

	private void tryAddMember(Timeline timeline, TimelineTrackListState trackListState, String eventId) {
		if (timeline == null || eventId == null || eventId.isBlank() || members.containsKey(eventId)) {
			return;
		}
		TimelineEventRef ref = TimelineEventRefs.find(timeline, eventId);
		if (ref == null || ref.event() == null || ref.track() == null || ref.clip() == null) {
			return;
		}
		if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, ref.track().getId())) {
			return;
		}
		if (TimelineEventMovePolicy.boundsFor(ref.clip(), ref.event()).isFixed()) {
			return;
		}
		members.put(eventId, new Member(
			ref.track().getId(),
			ref.clip().getId(),
			eventId,
			ref.event().getTimeSeconds()
		));
	}

	public void restoreInitialTimes(Timeline timeline) {
		if (timeline == null) {
			return;
		}
		for (Member member : members.values()) {
			TimelineEventRef ref = TimelineEventRefs.find(timeline, member.eventId());
			if (ref == null || ref.event() == null) {
				continue;
			}
			ref.event().setTimeSeconds(member.initialTimeSeconds());
			if (Timeline.isAnimationEventsTrackId(member.trackId())) {
				timeline.markAnimationEventsDirty(member.trackId());
			}
		}
	}

	public List<EventMoveSnapshot> collectMoveSnapshots(Timeline timeline) {
		List<EventMoveSnapshot> moves = new ArrayList<>();
		if (timeline == null) {
			return moves;
		}
		for (Member member : members.values()) {
			TimelineEventRef ref = TimelineEventRefs.find(timeline, member.eventId());
			if (ref == null || ref.event() == null) {
				continue;
			}
			double newTime = ref.event().getTimeSeconds();
			if (Math.abs(newTime - member.initialTimeSeconds()) < 1e-9) {
				continue;
			}
			moves.add(new EventMoveSnapshot(
				member.trackId(),
				member.clipId(),
				member.eventId(),
				member.initialTimeSeconds(),
				newTime
			));
		}
		return moves;
	}

	public void clear() {
		primaryEventId = null;
		initialTimeSeconds = 0.0;
		members.clear();
	}
}
