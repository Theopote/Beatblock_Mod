package com.beatblock.timeline.project;

import com.beatblock.BeatBlock;
import com.beatblock.automap.vfx.EnvironmentLightingRuntime;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.client.camera.TimelineCameraController;
import com.beatblock.client.render.GlobalVisualEffectOverlay;
import com.beatblock.timeline.TimelineEditor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Centralized project-switch / Close runtime cleanup.
 * New, Open success, and Close share the same presentation-reset contract.
 */
public final class ProjectRuntimeResetService {

	public enum Mode {
		/** Before document swap attempt (cancel gesture / stop playback). */
		PREPARE_SWITCH,
		/** After successful New / Open (editor transient + presentation reset). */
		AFTER_DOCUMENT_SWAP,
		/** Close BeatBlock UI — full presentation teardown + selection clear. */
		CLOSE_UI
	}

	private final Supplier<@Nullable TimelineEditor> timelineEditor;

	/** Test-only: last presentation steps executed by {@link #clearPresentationSafely()}. */
	static @Nullable volatile List<String> lastPresentationStepsForTests;

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
		// New/Open success: drop previous project's camera/VFX presentation.
		clearPresentationSafely();
	}

	private void closeUi() {
		prepareSwitch();
		afterDocumentSwap();
		clearWorldSelectionSafely();
	}

	/**
	 * Full presentation teardown using existing Camera / VFX / Environment APIs.
	 * Idempotent; safe when Minecraft / driver is unavailable (unit tests).
	 */
	static void clearPresentationSafely() {
		List<String> steps = new ArrayList<>(4);
		runPresentationStep(steps, "camera", () ->
			TimelineCameraController.getInstance().onTimelineUiClosed());
		runPresentationStep(steps, "vfx_overlay", GlobalVisualEffectOverlay::clear);
		runPresentationStep(steps, "environment_lighting", EnvironmentLightingRuntime::clear);
		runPresentationStep(steps, "stop_driving_if_needed", () -> {
			// stopPlayback already clears driving+VFX+camera; call only if still driving.
			if (BeatBlockClientDriver.isDriving()) {
				BeatBlockClientDriver.stopDriving();
			}
		});
		lastPresentationStepsForTests = List.copyOf(steps);
	}

	private static void runPresentationStep(List<String> steps, String id, Runnable action) {
		try {
			action.run();
			steps.add(id);
		} catch (Throwable error) {
			BeatBlock.LOGGER.debug("Skip presentation reset step {}", id, error);
		}
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
