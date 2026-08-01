package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import org.jspecify.annotations.Nullable;

/**
 * Session-level gate for formal play: runs {@link TimelineValidator}, stores the last report,
 * and optionally opens the Performance check UI.
 */
public final class PerformanceCheckController {

	private static @Nullable TimelineValidationReport lastReport;
	private static boolean openDialogRequested;
	private static boolean showProblemsExpanded;
	/** Play action deferred when blocked by errors; used by Force Play. */
	private static @Nullable Runnable blockedPlayAction;

	private PerformanceCheckController() {}

	public static @Nullable TimelineValidationReport lastReport() {
		return lastReport;
	}

	public static void clear() {
		lastReport = null;
		openDialogRequested = false;
		showProblemsExpanded = false;
		blockedPlayAction = null;
	}

	/**
	 * Validate and decide whether {@code onAllowed} may run (start play).
	 *
	 * @return the report (never null)
	 */
	public static TimelineValidationReport gatePlay(
		@Nullable Timeline timeline,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layers,
		@Nullable Runnable onAllowed
	) {
		TimelineValidationReport report = TimelineValidator.validate(timeline, engine, layers);
		lastReport = report;

		if (report.hasErrors()) {
			blockedPlayAction = onAllowed;
			showProblemsExpanded = true;
			openDialogRequested = true;
			return report;
		}

		blockedPlayAction = null;
		if (report.hasWarnings()) {
			openDialogRequested = true;
			showProblemsExpanded = false;
		} else {
			openDialogRequested = false;
		}

		if (onAllowed != null) {
			onAllowed.run();
		}
		return report;
	}

	/** Open Performance check for the current timeline without starting play. */
	public static TimelineValidationReport checkOnly(
		@Nullable Timeline timeline,
		@Nullable BlockAnimationEngine engine,
		@Nullable BuildLayerManager layers
	) {
		TimelineValidationReport report = TimelineValidator.validate(timeline, engine, layers);
		lastReport = report;
		openDialogRequested = true;
		showProblemsExpanded = report.hasErrors() || report.hasWarnings();
		blockedPlayAction = null;
		return report;
	}

	public static boolean consumeOpenDialogRequest() {
		if (!openDialogRequested) {
			return false;
		}
		openDialogRequested = false;
		return true;
	}

	public static boolean showProblemsExpanded() {
		return showProblemsExpanded;
	}

	public static void setShowProblemsExpanded(boolean expanded) {
		showProblemsExpanded = expanded;
	}

	/** User dismissed the dialog. */
	public static void dismissDialog() {
		blockedPlayAction = null;
		openDialogRequested = false;
	}

	/**
	 * Force play despite errors (power-user override from the dialog).
	 */
	public static void forcePlayDespiteErrors() {
		Runnable action = blockedPlayAction;
		blockedPlayAction = null;
		if (action != null) {
			action.run();
		}
	}

	public static boolean hasBlockedPlayAction() {
		return blockedPlayAction != null;
	}
}
