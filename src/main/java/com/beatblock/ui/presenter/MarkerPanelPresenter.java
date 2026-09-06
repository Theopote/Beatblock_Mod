package com.beatblock.ui.presenter;

import com.beatblock.timeline.MarkerEditPolicy;
import com.beatblock.timeline.MarkerEditState;
import com.beatblock.timeline.MarkerOrigin;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.DeleteMarkerCommand;
import com.beatblock.timeline.command.UpdateMarkerCommand;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;
import com.beatblock.timeline.marker.MarkerInsertionService;
import com.beatblock.ui.i18n.BBTexts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Marker Creator 面板业务逻辑：列表、编辑、删除与相邻 Marker 查询。
 */
public final class MarkerPanelPresenter {

	public record MarkerListItem(
		String id,
		String listLabel,
		int colorAbgr,
		String name,
		double timeSeconds,
		MarkerType type,
		MarkerOrigin origin,
		MarkerEditState editState
	) {}

	public record MarkerFormSnapshot(
		String name,
		String timeText,
		int typeIndex,
		MarkerOrigin origin,
		MarkerEditState editState,
		boolean locked
	) {}

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
			return new MarkerFormSnapshot("", "0.000", 0, MarkerOrigin.MANUAL, MarkerEditState.USER_EDITED, false);
		}
		return new MarkerFormSnapshot(
			marker.getName(),
			formatTime(marker.getTimeSeconds()),
			clampTypeIndex(marker.getType().ordinal()),
			marker.getOrigin(),
			marker.getEditState(),
			MarkerEditPolicy.isLocked(marker)
		);
	}

	/**
	 * @param structuralConfirmed 对 SECTION/GENERATED 的类型切换已二次确认
	 */
	public MarkerEditOutcome applyMarkerEdit(
		Timeline timeline,
		String markerId,
		String rawName,
		String rawTime,
		int typeIndex,
		boolean structuralConfirmed
	) {
		if (timeline == null || markerId == null || markerId.isBlank()) {
			return new MarkerEditOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.no_marker")), null);
		}
		TimelineMarker marker = findMarker(timeline, markerId);
		if (marker == null) {
			return new MarkerEditOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.marker_not_found")), null);
		}
		double timeSeconds = marker.getTimeSeconds();
		try {
			if (rawTime != null && !rawTime.isBlank()) {
				timeSeconds = Math.max(0.0, Double.parseDouble(rawTime.trim()));
			}
		} catch (NumberFormatException ex) {
			return new MarkerEditOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.invalid_time_format")),
				formSnapshotFor(marker)
			);
		}
		return applyMarkerEdit(timeline, markerId, rawName, timeSeconds, typeIndex, structuralConfirmed);
	}

	/**
	 * Apply marker edits with an already-resolved timeline time in seconds
	 * (e.g. from {@link com.beatblock.ui.util.MusicalDurationField}).
	 */
	public MarkerEditOutcome applyMarkerEdit(
		Timeline timeline,
		String markerId,
		String rawName,
		double timeSeconds,
		int typeIndex,
		boolean structuralConfirmed
	) {
		if (timeline == null || markerId == null || markerId.isBlank()) {
			return new MarkerEditOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.no_marker")), null);
		}
		TimelineMarker marker = findMarker(timeline, markerId);
		if (marker == null) {
			return new MarkerEditOutcome(PresenterResult.failure(BBTexts.get("beatblock.message.marker_not_found")), null);
		}
		if (MarkerEditPolicy.isLocked(marker)) {
			return new MarkerEditOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.marker_locked")),
				formSnapshotFor(marker)
			);
		}

		String name = rawName != null ? rawName.trim() : "";
		double safeTime = Math.max(0.0, timeSeconds);

		MarkerType type = MarkerType.values()[clampTypeIndex(typeIndex)];
		if (type != marker.getType()
			&& !MarkerEditPolicy.allowsMutation(
				marker, MarkerEditPolicy.StructuralAction.CHANGE_TYPE, type, structuralConfirmed)) {
			return new MarkerEditOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.marker_structural_confirm_required")),
				formSnapshotFor(marker)
			);
		}

		TimelineMarker after = marker.withFields(safeTime, name, type, true);
		if (!executeUpdate(timeline, marker, after)) {
			return new MarkerEditOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.message.marker_section_collision")),
				formSnapshotFor(marker)
			);
		}
		TimelineMarker updated = findMarker(timeline, markerId);
		return new MarkerEditOutcome(
			PresenterResult.success(""),
			formSnapshotFor(updated != null ? updated : after)
		);
	}

	/** 兼容旧调用：无结构确认。 */
	public MarkerEditOutcome applyMarkerEdit(
		Timeline timeline,
		String markerId,
		String rawName,
		String rawTime,
		int typeIndex
	) {
		return applyMarkerEdit(timeline, markerId, rawName, rawTime, typeIndex, false);
	}

	public boolean requiresTypeChangeConfirm(TimelineMarker marker, int typeIndex) {
		if (marker == null) {
			return false;
		}
		MarkerType type = MarkerType.values()[clampTypeIndex(typeIndex)];
		return MarkerEditPolicy.requiresStructuralConfirm(
			marker, MarkerEditPolicy.StructuralAction.CHANGE_TYPE, type);
	}

	public boolean requiresDeleteConfirm(TimelineMarker marker) {
		return MarkerEditPolicy.requiresStructuralConfirm(
			marker, MarkerEditPolicy.StructuralAction.DELETE, null);
	}

	public PresenterResult deleteMarker(Timeline timeline, String markerId, boolean structuralConfirmed) {
		if (timeline == null || markerId == null || markerId.isBlank()) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.no_marker"));
		}
		TimelineMarker marker = findMarker(timeline, markerId);
		if (marker == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.marker_not_found"));
		}
		if (MarkerEditPolicy.isLocked(marker)) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.marker_locked"));
		}
		if (!MarkerEditPolicy.allowsMutation(
			marker, MarkerEditPolicy.StructuralAction.DELETE, null, structuralConfirmed)) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.marker_structural_confirm_required"));
		}
		DeleteMarkerCommand command = new DeleteMarkerCommand(timeline, marker);
		CommandManager commands = commandManager();
		if (commands != null) {
			commands.execute(command);
		} else {
			command.execute();
		}
		if (!command.wasApplied()) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.marker_not_found"));
		}
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		return PresenterResult.success("");
	}

	public PresenterResult deleteMarker(Timeline timeline, String markerId) {
		return deleteMarker(timeline, markerId, false);
	}

	/**
	 * Lock / Unlock。Unlock 固定回到 {@link MarkerEditState#USER_EDITED}，
	 * 避免解锁后再次被 re-analysis 静默覆盖。
	 */
	public PresenterResult setMarkerLocked(Timeline timeline, String markerId, boolean locked) {
		if (timeline == null || markerId == null || markerId.isBlank()) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.no_marker"));
		}
		TimelineMarker marker = findMarker(timeline, markerId);
		if (marker == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.marker_not_found"));
		}
		boolean currentlyLocked = MarkerEditPolicy.isLocked(marker);
		if (currentlyLocked == locked) {
			return PresenterResult.success("");
		}
		MarkerEditState nextState = locked ? MarkerEditState.LOCKED : MarkerEditState.USER_EDITED;
		TimelineMarker after = marker.withEditState(nextState);
		if (!executeUpdate(timeline, marker, after)) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.marker_section_collision"));
		}
		return PresenterResult.success("");
	}

	public PresenterResult insertAtPlayhead(MarkerType type, String name) {
		Timeline timeline = currentTimeline();
		TimelineEditor editor = editorPresenter != null ? editorPresenter.editorOrNull() : null;
		if (timeline == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.marker.no_timeline"));
		}
		double time = editor != null
			? editor.getPlaybackSession().currentTimeSeconds()
			: 0.0;
		String markerName = name != null && !name.isBlank()
			? name.trim()
			: defaultNameForType(type, timeline);
		var result = MarkerInsertionService.insertManual(
			timeline,
			editor,
			new MarkerInsertionService.CreationRequest(time, markerName, type != null ? type : MarkerType.GENERIC)
		);
		return result.written()
			? PresenterResult.success("")
			: PresenterResult.failure(BBTexts.get("beatblock.message.no_marker"));
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

	private boolean executeUpdate(Timeline timeline, TimelineMarker before, TimelineMarker after) {
		UpdateMarkerCommand command = new UpdateMarkerCommand(timeline, before, after);
		CommandManager commands = commandManager();
		if (commands != null) {
			commands.execute(command);
		} else {
			command.execute();
		}
		if (!command.wasApplied()) {
			return false;
		}
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		return true;
	}

	private CommandManager commandManager() {
		TimelineEditor editor = editorPresenter != null ? editorPresenter.editorOrNull() : null;
		return editor != null ? editor.getCommandManager() : null;
	}

	private static String defaultNameForType(MarkerType type, Timeline timeline) {
		if (type == MarkerType.SECTION) {
			return "SECTION";
		}
		int markerIndex = timeline.getMarkers().size() + 1;
		return "Marker " + markerIndex;
	}

	private static MarkerListItem toListItem(TimelineMarker marker) {
		String displayName = marker.getName() == null || marker.getName().isBlank()
			? "(unnamed)"
			: marker.getName();
		String stateTag = switch (marker.getEditState()) {
			case GENERATED -> "*";
			case LOCKED -> "[L]";
			case USER_EDITED -> "";
		};
		String listLabel = String.format(Locale.ROOT, "[%s]%s %.2fs  %s",
			marker.getType().getDisplayName(),
			stateTag.isEmpty() ? "" : " " + stateTag,
			marker.getTimeSeconds(),
			displayName);
		return new MarkerListItem(
			marker.getId(),
			listLabel,
			marker.getType().getColorAbgr(),
			marker.getName(),
			marker.getTimeSeconds(),
			marker.getType(),
			marker.getOrigin(),
			marker.getEditState()
		);
	}
}
