package com.beatblock.timeline.playback;

import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

/**
 * ImGui modal: Performance check summary + filterable problem list + jump-to.
 */
public final class PerformanceCheckDialog {

	public static final String POPUP_ID = "##BB_PerformanceCheck";

	private PerformanceCheckDialog() {}

	/** Call once per frame from the main UI loop. */
	public static void render() {
		if (PerformanceCheckController.consumeOpenDialogRequest()) {
			ImGui.openPopup(POPUP_ID);
		}

		TimelineValidationReport report = PerformanceCheckController.lastReport();
		if (report == null) {
			return;
		}

		ImGui.setNextWindowSize(520f, 0f, ImGuiCond.Appearing);
		if (!ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.AlwaysAutoResize)) {
			if (!ImGui.isPopupOpen(POPUP_ID)) {
				PerformanceCheckController.dismissDialog();
			}
			return;
		}

		ImGui.text(BBTexts.get("beatblock.performance_check.title"));
		ImGui.separator();
		ImGui.spacing();

		drawCheckLine(BBTexts.get(
			"beatblock.performance_check.count_animation",
			report.animationEventCount()
		));
		drawCheckLine(BBTexts.get(
			"beatblock.performance_check.count_camera",
			report.cameraKeyframeCount()
		));
		drawCheckLine(BBTexts.get(
			"beatblock.performance_check.count_layers",
			report.buildLayerCount()
		));
		if (report.markerCount() > 0) {
			drawCheckLine(BBTexts.get(
				"beatblock.performance_check.count_markers",
				report.markerCount()
			));
		}

		ImGui.spacing();
		int errors = report.errorCount();
		int warnings = report.warningCount();
		if (errors == 0 && warnings == 0) {
			ImGui.textColored(0.4f, 0.9f, 0.5f, 1f, BBTexts.get("beatblock.performance_check.clean"));
		} else {
			if (warnings > 0) {
				ImGui.textColored(1f, 0.85f, 0.25f, 1f,
					BBTexts.get("beatblock.performance_check.warnings", warnings));
			}
			if (errors > 0) {
				ImGui.textColored(1f, 0.4f, 0.4f, 1f,
					BBTexts.get("beatblock.performance_check.errors", errors));
			}
		}

		ImGui.spacing();
		boolean expanded = PerformanceCheckController.showProblemsExpanded();
		String toggleLabel = expanded
			? BBTexts.get("beatblock.performance_check.hide_problems")
			: BBTexts.get("beatblock.performance_check.view_problems");
		if (ImGui.button(toggleLabel + "##pcToggleProblems")) {
			PerformanceCheckController.setShowProblemsExpanded(!expanded);
		}

		if (PerformanceCheckController.showProblemsExpanded()) {
			ImGui.separator();
			// Filters
			int filter = PerformanceCheckController.problemFilterMode();
			if (ImGui.radioButton(BBTexts.get("beatblock.performance_check.filter_all") + "##pcFAll", filter == PerformanceCheckController.FILTER_ALL)) {
				PerformanceCheckController.setProblemFilterMode(PerformanceCheckController.FILTER_ALL);
			}
			ImGui.sameLine();
			if (ImGui.radioButton(BBTexts.get("beatblock.performance_check.filter_errors") + "##pcFErr", filter == PerformanceCheckController.FILTER_ERRORS)) {
				PerformanceCheckController.setProblemFilterMode(PerformanceCheckController.FILTER_ERRORS);
			}
			ImGui.sameLine();
			if (ImGui.radioButton(BBTexts.get("beatblock.performance_check.filter_warnings") + "##pcFWarn", filter == PerformanceCheckController.FILTER_WARNINGS)) {
				PerformanceCheckController.setProblemFilterMode(PerformanceCheckController.FILTER_WARNINGS);
			}

			List<TimelineDiagnostic> problems = PerformanceCheckController.filteredProblems();
			ImGui.beginChild("##pcProblems", 0f, 220f, true);
			int row = 0;
			for (TimelineDiagnostic d : problems) {
				boolean err = d.severity() == TimelineDiagnosticSeverity.ERROR;
				String prefix = err ? "✕ " : "⚠ ";
				if (err) {
					ImGui.textColored(1f, 0.45f, 0.45f, 1f, prefix + d.message());
				} else {
					ImGui.textColored(1f, 0.85f, 0.3f, 1f, prefix + d.message());
				}
				boolean canJump = d.hasTime() || d.eventId() != null;
				if (canJump) {
					StringBuilder meta = new StringBuilder();
					if (d.eventId() != null) {
						meta.append(d.eventId());
					}
					if (d.hasTime()) {
						if (d.eventId() != null) {
							meta.append(" · ");
						}
						meta.append(String.format("%.2fs", d.timeSeconds()));
					}
					ImGui.sameLine();
					if (ImGui.smallButton(BBTexts.get("beatblock.performance_check.jump") + "##pcJump" + row)) {
						PerformanceCheckController.requestJumpTo(
							d.eventId(),
							d.hasTime() ? d.timeSeconds() : 0
						);
					}
					if (ImGui.isItemHovered()) {
						ImGui.setTooltip(BBTexts.get("beatblock.performance_check.jump.tooltip") + "\n" + meta);
					}
					ImGui.textDisabled("    " + meta);
				}
				row++;
			}
			if (problems.isEmpty()) {
				ImGui.textDisabled(BBTexts.get("beatblock.performance_check.no_problems"));
			}
			ImGui.endChild();
		}

		ImGui.spacing();
		ImGui.separator();
		ImGui.spacing();

		if (report.hasErrors()) {
			ImGui.textWrapped(BBTexts.get("beatblock.performance_check.blocked"));
			ImGui.spacing();
			if (PerformanceCheckController.hasBlockedPlayAction()) {
				if (ImGui.button(BBTexts.get("beatblock.performance_check.force_play") + "##pcForce", 160f, 0f)) {
					PerformanceCheckController.forcePlayDespiteErrors();
					ImGui.closeCurrentPopup();
				}
				if (ImGui.isItemHovered()) {
					ImGui.setTooltip(BBTexts.get("beatblock.performance_check.force_play.tooltip"));
				}
				ImGui.sameLine();
			}
		}

		if (ImGui.button(BBTexts.get("beatblock.common.close") + "##pcClose", 120f, 0f)) {
			PerformanceCheckController.dismissDialog();
			ImGui.closeCurrentPopup();
		}

		ImGui.endPopup();
	}

	private static void drawCheckLine(String text) {
		ImGui.textColored(0.45f, 0.9f, 0.55f, 1f, "✓  " + text);
	}
}
