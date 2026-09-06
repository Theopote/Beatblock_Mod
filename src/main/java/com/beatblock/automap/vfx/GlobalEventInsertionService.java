package com.beatblock.automap.vfx;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.ApplyEnvironmentPresetCommand;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.CreateGlobalEventCommand;
import com.beatblock.timeline.editing.TimelineDocumentChangeNotifier;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.ui.TimelinePanelVisibility;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Creator gateway for global / VFX inserts: Command + one Undo + select clip/event + open Properties + notify.
 * Multi-cue {@link EnvironmentPreset} applies go through {@link ApplyEnvironmentPresetCommand}
 * so Lighting+Weather+Tint is still one Ctrl+Z.
 */
public final class GlobalEventInsertionService {

	public record InsertionResult(@Nullable String clipId, @Nullable String eventId) {
		public static final InsertionResult EMPTY = new InsertionResult(null, null);

		public boolean written() {
			return clipId != null && !clipId.isBlank() && eventId != null && !eventId.isBlank();
		}
	}

	public record PresetInsertionResult(
		@Nullable String presetId,
		List<String> clipIds,
		List<String> eventIds
	) {
		public static final PresetInsertionResult EMPTY = new PresetInsertionResult(null, List.of(), List.of());

		public PresetInsertionResult {
			clipIds = List.copyOf(clipIds != null ? clipIds : List.of());
			eventIds = List.copyOf(eventIds != null ? eventIds : List.of());
		}

		public boolean written() {
			return !eventIds.isEmpty();
		}

		public int writtenCount() {
			return eventIds.size();
		}
	}

	private GlobalEventInsertionService() {
	}

	public static InsertionResult insertManual(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		@Nullable GlobalEventCreationRequest request
	) {
		if (timeline == null || editor == null || request == null) {
			return InsertionResult.EMPTY;
		}
		CommandManager commandManager = editor.getCommandManager();
		CreateGlobalEventCommand command = new CreateGlobalEventCommand(
			timeline,
			request,
			TimelineGenerationMetadata.manual()
		);
		commandManager.execute(command);
		if (!command.wasApplied()) {
			return InsertionResult.EMPTY;
		}
		String clipId = Objects.requireNonNull(command.createdClipId());
		String eventId = Objects.requireNonNull(command.createdEventId());
		SelectionState selection = editor.getSelectionState();
		selection.clearEvents();
		selection.clearClips();
		selection.selectClip(clipId);
		selection.selectEvent(eventId);
		editor.syncClockDuration();
		TimelinePanelVisibility.openTimelineProperties();
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		return new InsertionResult(clipId, eventId);
	}

	/**
	 * Apply a named environment preset at {@code timeSeconds} as one Undo
	 * (N typed global events under one {@link ApplyEnvironmentPresetCommand}).
	 */
	public static PresetInsertionResult applyPreset(
		@Nullable Timeline timeline,
		@Nullable TimelineEditor editor,
		@Nullable EnvironmentPreset preset,
		double timeSeconds
	) {
		if (timeline == null || editor == null || preset == null) {
			return PresetInsertionResult.EMPTY;
		}
		CommandManager commandManager = editor.getCommandManager();
		ApplyEnvironmentPresetCommand command = ApplyEnvironmentPresetCommand.of(
			timeline, Math.max(0.0, timeSeconds), preset);
		commandManager.execute(command);
		if (!command.wasApplied()) {
			return PresetInsertionResult.EMPTY;
		}
		List<String> clipIds = command.createdClipIds();
		List<String> eventIds = command.createdEventIds();
		SelectionState selection = editor.getSelectionState();
		selection.clearEvents();
		selection.clearClips();
		for (String clipId : clipIds) {
			selection.selectClip(clipId);
		}
		for (String eventId : eventIds) {
			selection.selectEvent(eventId);
		}
		if (!eventIds.isEmpty()) {
			selection.setRangeAnchorEventId(eventIds.getFirst());
		}
		editor.syncClockDuration();
		TimelinePanelVisibility.openTimelineProperties();
		TimelineDocumentChangeNotifier.notifyDocumentEdited();
		return new PresetInsertionResult(preset.id(), clipIds, eventIds);
	}
}
