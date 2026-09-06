package com.beatblock.timeline.marker;

import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.CreateMarkerCommand;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;
import org.jspecify.annotations.Nullable;

/**
 * Marker 创建网关：Command → one Undo → DocumentChangeNotifier。
 */
public final class MarkerInsertionService {

	public record CreationRequest(double timeSeconds, @Nullable String name, @Nullable MarkerType type) {
		public CreationRequest {
			timeSeconds = Math.max(0.0, timeSeconds);
			name = name != null ? name : "";
			type = type != null ? type : MarkerType.GENERIC;
		}
	}

	public record InsertionResult(@Nullable String markerId) {
		public static final InsertionResult EMPTY = new InsertionResult(null);

		public boolean written() {
			return markerId != null && !markerId.isBlank();
		}
	}

	private MarkerInsertionService() {
	}

	public static InsertionResult insertManual(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		@Nullable CreationRequest request
	) {
		if (timeline == null || request == null) {
			return InsertionResult.EMPTY;
		}
		TimelineMarker marker = TimelineMarker.manual(request.timeSeconds(), request.name(), request.type());
		if (editor != null) {
			CommandManager commands = editor.getCommandManager();
			CreateMarkerCommand command = new CreateMarkerCommand(timeline, marker);
			commands.execute(command);
			if (!command.wasApplied()) {
				return InsertionResult.EMPTY;
			}
		} else {
			if (!timeline.addMarker(marker)) {
				return InsertionResult.EMPTY;
			}
		}
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		MarkerFocusRequest.requestRename(marker.getId());
		return new InsertionResult(marker.getId());
	}

	public static InsertionResult insertAtTime(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		double timeSeconds
	) {
		int nextIndex = timeline != null ? timeline.getMarkers().size() + 1 : 1;
		return insertManual(
			timeline,
			editor,
			new CreationRequest(timeSeconds, "Marker " + nextIndex, MarkerType.GENERIC)
		);
	}

	/** 播放头快速创建：始终 GENERIC。 */
	public static InsertionResult insertGenericAtPlayhead(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		double timeSeconds
	) {
		return insertAtTime(timeline, editor, timeSeconds);
	}
}
