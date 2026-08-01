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
	/** Seek request from problem list (seconds); consumed by transport/UI. */
	private static @Nullable Double pendingSeekTimeSeconds;
	private static @Nullable String pendingSeekEventId;
	/** Problem list filter: 0=all, 1=errors only, 2=warnings only. */
	private static int problemFilterMode;

	private PerformanceCheckController() {}

	public static final int FILTER_ALL = 0;
	public static final int FILTER_ERRORS = 1;
	public static final int FILTER_WARNINGS = 2;

	public static @Nullable TimelineValidationReport lastReport() {
		return lastReport;
	}

	public static void clear() {
		lastReport = null;
		openDialogRequested = false;
		showProblemsExpanded = false;
		blockedPlayAction = null;
		pendingSeekTimeSeconds = null;
		pendingSeekEventId = null;
		problemFilterMode = FILTER_ALL;
	}

	public static int problemFilterMode() {
		return problemFilterMode;
	}

	public static void setProblemFilterMode(int mode) {
		if (mode < FILTER_ALL || mode > FILTER_WARNINGS) {
			mode = FILTER_ALL;
		}
		problemFilterMode = mode;
	}

	/** Problems filtered by {@link #problemFilterMode()}. */
	public static java.util.List<TimelineDiagnostic> filteredProblems() {
		TimelineValidationReport report = lastReport;
		if (report == null) {
			return java.util.List.of();
		}
		java.util.List<TimelineDiagnostic> out = new java.util.ArrayList<>();
		for (TimelineDiagnostic d : report.problems()) {
			if (problemFilterMode == FILTER_ERRORS
				&& d.severity() != TimelineDiagnosticSeverity.ERROR) {
				continue;
			}
			if (problemFilterMode == FILTER_WARNINGS
				&& d.severity() != TimelineDiagnosticSeverity.WARNING) {
				continue;
			}
			out.add(d);
		}
		return out;
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
			blockedPlayAction = report.hasFatalErrors() ? null : onAllowed;
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

	/**
	 * Request playhead/view jump to a diagnostic location (problem list click).
	 */
	public static void requestJumpTo(@Nullable String eventId, double timeSeconds) {
		if (!Double.isNaN(timeSeconds) && timeSeconds >= 0) {
			pendingSeekTimeSeconds = timeSeconds;
		} else {
			pendingSeekTimeSeconds = null;
		}
		pendingSeekEventId = eventId != null && !eventId.isBlank() ? eventId : null;
	}

	public static @Nullable Double consumePendingSeekTime() {
		Double t = pendingSeekTimeSeconds;
		pendingSeekTimeSeconds = null;
		return t;
	}

	public static @Nullable String consumePendingSeekEventId() {
		String id = pendingSeekEventId;
		pendingSeekEventId = null;
		return id;
	}

	public static boolean hasPendingJump() {
		return pendingSeekTimeSeconds != null || pendingSeekEventId != null;
	}
}
