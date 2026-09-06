package com.beatblock.automap.camera;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.CreateCameraClipCommand;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.ui.TimelinePanelVisibility;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Creator gateway for camera-track inserts: Command + one Undo + select clip/segment + open Properties + notify.
 * <p>
 * Semantic Creator shots start from StageObject subject + framing + movement; geometry is solved by
 * {@link CameraFramingEngine} inside {@link CameraShotTimelineWriter}.
 */
public final class CameraShotInsertionService {

	/** Matches {@code CameraTrackFactory} default path segment duration. */
	public static final double DEFAULT_PATH_DURATION_SECONDS = 4.0;
	/** Matches {@code CameraTrackFactory} default procedural segment duration. */
	public static final double DEFAULT_PROC_DURATION_SECONDS = 3.0;

	public record InsertionResult(@Nullable String clipId) {
		public static final InsertionResult EMPTY = new InsertionResult(null);

		public boolean written() {
			return clipId != null && !clipId.isBlank();
		}
	}

	private CameraShotInsertionService() {
	}

	/**
	 * Insert a semantic {@link CameraShot} as MANUAL content (Camera Creator Panel).
	 */
	public static InsertionResult insertManualShot(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		@Nullable CameraShot shot
	) {
		if (shot == null) {
			return InsertionResult.EMPTY;
		}
		return insertManualDraft(timeline, editor, CameraShotDraft.semantic(shot));
	}

	/**
	 * Insert a {@link CameraShotDraft} (semantic or pose-anchored) as MANUAL content.
	 */
	public static InsertionResult insertManualDraft(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		@Nullable CameraShotDraft draft
	) {
		if (timeline == null || editor == null || draft == null || draft.shot() == null) {
			return InsertionResult.EMPTY;
		}
		if (CameraShotValidator.hasErrors(CameraShotValidator.validate(draft.shot()))) {
			return InsertionResult.EMPTY;
		}
		CreateCameraClipCommand command = new CreateCameraClipCommand(timeline, tl ->
			CameraShotTimelineWriter.writeTaggedDrafts(
				tl,
				List.of(new CameraShotTimelineWriter.TaggedDraft(draft, TimelineGenerationMetadata.manual()))
			)
		);
		return commitManual(timeline, editor, command);
	}

	private static InsertionResult commitManual(
		Timeline timeline,
		TimelineEditor editor,
		CreateCameraClipCommand command
	) {
		CommandManager commandManager = editor.getCommandManager();
		commandManager.execute(command);
		if (!command.wasApplied()) {
			return InsertionResult.EMPTY;
		}
		String clipId = Objects.requireNonNull(command.createdClipId());
		SelectionState selection = editor.getSelectionState();
		selection.clearEvents();
		selection.clearClips();
		selection.selectClip(clipId);
		Track cam = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
		Clip clip = cam != null ? cam.getClip(clipId) : null;
		TimelineEvent segment = CameraTrackFactory.findSegmentHeadEvent(clip);
		if (segment != null) {
			selection.selectEvent(segment.getId());
		}
		editor.syncClockDuration();
		TimelinePanelVisibility.openTimelineProperties();
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		return new InsertionResult(clipId);
	}
}
