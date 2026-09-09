package com.beatblock.timeline.project;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.TimelineEditor;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Centralized project-switch / Close runtime cleanup.
 * New, Open success, and Close share this contract.
 */
public final class ProjectRuntimeResetService {

	public enum Mode {
		/** Before document swap attempt (cancel gesture / stop playback). */
		PREPARE_SWITCH,
		/** After successful New / Open (clear edit transient + sync clock). */
		AFTER_DOCUMENT_SWAP,
		/** Close BeatBlock UI (full presentation teardown). */
		CLOSE_UI
	}

	private final Supplier<@Nullable TimelineEditor> timelineEditor;

	public ProjectRuntimeResetService(Supplier<@Nullable TimelineEditor> timelineEditor) {
		this.timelineEditor = timelineEditor != null ? timelineEditor : () -> null;
	}

	public void reset(Mode mode) {
		if (mode == null) {
			return;
		}
		switch (mode) {
			case PREPARE_SWITCH -> prepareSwitch();
			case AFTER_DOCUMENT_SWAP -> afterDocumentSwap();
			case CLOSE_UI -> closeUi();
		}
	}

	private void prepareSwitch() {
		TimelineEditor editor = timelineEditor.get();
		if (editor != null) {
			try {
				editor.cancelLiveDocumentPreview();
			} catch (Throwable error) {
				BeatBlock.LOGGER.debug("Skip cancelLiveDocumentPreview", error);
			}
		}
		stopPlaybackSafely();
	}

	private void afterDocumentSwap() {
		TimelineEditor editor = timelineEditor.get();
		if (editor != null) {
			try {
				editor.clearTransientEditState();
				editor.syncClockDuration();
			} catch (Throwable error) {
				BeatBlock.LOGGER.debug("Skip clearTransientEditState/syncClock", error);
			}
		}
	}

	private void closeUi() {
		prepareSwitch();
		afterDocumentSwap();
		clearWorldSelectionSafely();
	}

	private static void stopPlaybackSafely() {
		try {
			BeatBlockClientDriver.stopPlayback();
		} catch (Throwable error) {
			BeatBlock.LOGGER.debug("Skip stopPlayback during project reset", error);
		}
	}

	private static void clearWorldSelectionSafely() {
		try {
			com.beatblock.selection.BeatBlockSelectionManager.get().clearSelection();
		} catch (Throwable error) {
			BeatBlock.LOGGER.debug("Skip selection clear during project reset", error);
		}
	}
}
