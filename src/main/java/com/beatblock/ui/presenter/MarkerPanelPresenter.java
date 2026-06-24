package com.beatblock.ui.presenter;

import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Marker 面板业务逻辑：列表、编辑、删除与相邻 Marker 查询。
 */
public final class MarkerPanelPresenter {

	public record MarkerListItem(
		String id,
		String listLabel,
		int colorAbgr,
		String name,
		double timeSeconds,
		MarkerType type
	) {}

	public record MarkerFormSnapshot(String name, String timeText, int typeIndex) {}

	public record MarkerNeighbors(TimelineMarker previous, TimelineMarker next) {}

	public record MarkerEditOutcome(PresenterResult result, MarkerFormSnapshot formSnapshot) {}

	private final TimelineEditorPresenter editorPresenter;
	private final Supplier<Timeline> timeline;

	public MarkerPanelPresenter(TimelineEditorPresenter editorPresenter, Supplier<Timeline> timeline) {
		this.editorPresenter = editorPresenter;
		this.timeline = timeline;
	}

	public Timeline currentTimeline() {
		return timeline != null ? timeline.get() : null;
	}

	public TimelineEditorPresenter editorPresenter() {
		return editorPresenter;
	}

	public BeatBlockClientDriver.TimelineActionExecutionReport lastActionExecutionReport() {
		return BeatBlockClientDriver.getLastTimelineActionExecutionReport();
	}

	public List<MarkerListItem> listMarkers(Timeline timeline) {
		if (timeline == null || timeline.getMarkers().isEmpty()) {
			return List.of();
		}
		List<MarkerListItem> items = new ArrayList<>();
		for (TimelineMarker marker : timeline.getMarkers()) {
			if (marker == null) {
				continue;
			}
			items.add(toListItem(marker));
		}
		return items;
	}

	public TimelineMarker findMarker(Timeline timeline, String markerId) {
		if (timeline == null || markerId == null || markerId.isBlank()) {
			return null;
		}
		int index = timeline.findMarkerIndexById(markerId);
		if (index < 0) {
			return null;
		}
		return timeline.getMarkers().get(index);
	}

	public boolean markerExists(Timeline timeline, String markerId) {
		return findMarker(timeline, markerId) != null;
	}

	public MarkerFormSnapshot formSnapshotFor(TimelineMarker marker) {
		if (marker == null) {
			return new MarkerFormSnapshot("", "0.000", 0);
		}
		return new MarkerFormSnapshot(
			marker.getName(),
			formatTime(marker.getTimeSeconds()),
			clampTypeIndex(marker.getType().ordinal())
		);
	}

	public MarkerEditOutcome applyMarkerEdit(
		Timeline timeline,
		String markerId,
		String rawName,
		String rawTime,
		int typeIndex
	) {
		if (timeline == null || markerId == null || markerId.isBlank()) {
			return new MarkerEditOutcome(PresenterResult.failure("无可用 Marker。"), null);
		}
		TimelineMarker marker = findMarker(timeline, markerId);
		if (marker == null) {
			return new MarkerEditOutcome(PresenterResult.failure("Marker 不存在。"), null);
		}

		String name = rawName != null ? rawName.trim() : "";
		double timeSeconds = marker.getTimeSeconds();
		try {
			if (rawTime != null && !rawTime.isBlank()) {
				timeSeconds = Math.max(0.0, Double.parseDouble(rawTime.trim()));
			}
		} catch (NumberFormatException ex) {
			return new MarkerEditOutcome(
				PresenterResult.failure("时间格式不正确。"),
				formSnapshotFor(marker)
			);
		}

		MarkerType type = MarkerType.values()[clampTypeIndex(typeIndex)];
		timeline.updateMarker(markerId, timeSeconds, name, type);
		TimelineMarker updated = findMarker(timeline, markerId);
		return new MarkerEditOutcome(
			PresenterResult.success(""),
			formSnapshotFor(updated != null ? updated : marker)
		);
	}

	public PresenterResult deleteMarker(Timeline timeline, String markerId) {
		if (timeline == null || markerId == null || markerId.isBlank()) {
			return PresenterResult.failure("无可用 Marker。");
		}
		if (!timeline.removeMarker(markerId)) {
			return PresenterResult.failure("Marker 不存在。");
		}
		return PresenterResult.success("");
	}

	public MarkerNeighbors neighborsOf(Timeline timeline, String markerId) {
		if (timeline == null || markerId == null) {
			return new MarkerNeighbors(null, null);
		}
		int index = timeline.findMarkerIndexById(markerId);
		if (index < 0) {
			return new MarkerNeighbors(null, null);
		}
		TimelineMarker previous = index > 0 ? timeline.getMarkers().get(index - 1) : null;
		TimelineMarker next = index + 1 < timeline.getMarkers().size()
			? timeline.getMarkers().get(index + 1)
			: null;
		return new MarkerNeighbors(previous, next);
	}

	public boolean jumpToMarker(TimelineMarker marker) {
		if (marker == null) {
			return false;
		}
		return editorPresenter.seekPlayback(marker.getTimeSeconds());
	}

	public boolean setLoopInFromMarker(TimelineMarker marker) {
		return marker != null && editorPresenter.setLoopIn(marker.getTimeSeconds());
	}

	public boolean setLoopOutFromMarker(TimelineMarker marker) {
		return marker != null && editorPresenter.setLoopOut(marker.getTimeSeconds());
	}

	public boolean applyLoopRangeBetween(TimelineMarker startMarker, TimelineMarker endMarker) {
		if (startMarker == null || endMarker == null) {
			return false;
		}
		return editorPresenter.applyLoopRange(
			startMarker.getTimeSeconds(),
			endMarker.getTimeSeconds(),
			true
		);
	}

	public static String formatTime(double timeSeconds) {
		return String.format(Locale.ROOT, "%.3f", timeSeconds);
	}

	public static int clampTypeIndex(int typeIndex) {
		return Math.max(0, Math.min(typeIndex, MarkerType.values().length - 1));
	}

	private static MarkerListItem toListItem(TimelineMarker marker) {
		String displayName = marker.getName() == null || marker.getName().isBlank()
			? "(unnamed)"
			: marker.getName();
		String listLabel = String.format(Locale.ROOT, "[%s] %.2fs  %s",
			marker.getType().getDisplayName(),
			marker.getTimeSeconds(),
			displayName);
		return new MarkerListItem(
			marker.getId(),
			listLabel,
			marker.getType().getColorAbgr(),
			marker.getName(),
			marker.getTimeSeconds(),
			marker.getType()
		);
	}
}
