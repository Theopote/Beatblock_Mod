package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.audio.IAudioAnalyzer;
import com.beatblock.audio.python.PythonEnvironmentDiagnostics;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;

/** Python 运行时环境健康提示。 */
final class AudioAnalysisRuntimeControls {

	private AudioAnalysisRuntimeControls() {
	}

	static void renderPythonRuntimeHint(AudioAnalysisPanelHost host) {
		if (!host.presenter().isAnalyzerAvailable()) return;
		String py = host.presenter().pythonRuntimeSummary();
		if (py == null || py.isBlank()) return;
		PythonEnvironmentDiagnostics.RuntimeHealthSnapshot snapshot = host.presenter().runtimeHealthSnapshot();
		renderRuntimeHealth(py, snapshot);

		IAudioAnalyzer analyzer = host.presenter().backendAnalyzer();
		if (analyzer != null) {
			String backendLabel = analyzer.backendId();
			if (!analyzer.isAvailable()) {
				backendLabel += BBTexts.get("beatblock.audio.runtime.backend_unavailable");
			}
			ImGui.textDisabled(BBTexts.get("beatblock.audio.runtime.backend", backendLabel));
			if (host.presenter().activeAnalysisCount() > 0) {
				ImGui.sameLine();
				ImGui.textDisabled(BBTexts.get("beatblock.audio.runtime.in_progress", host.presenter().activeAnalysisCount()));
			}
		}

		ImGui.separator();
	}

	private static void renderRuntimeHealth(String pythonSummary, PythonEnvironmentDiagnostics.RuntimeHealthSnapshot snapshot) {
		if (snapshot == null) {
			ImGui.textDisabled(BBTexts.get("beatblock.audio.runtime.checking"));
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(pythonSummary);
			}
			return;
		}

		String summary = buildRuntimeHealthSummary(snapshot);
		ImVec4 color = runtimeStateColor(summaryState(snapshot));
		ImGui.pushStyleColor(ImGuiCol.Text, color.x, color.y, color.z, color.w);
		ImGui.textDisabled(summary);
		ImGui.popStyleColor();

		if (ImGui.isItemHovered()) {
			StringBuilder tooltip = new StringBuilder();
			tooltip.append(pythonSummary);
			appendHealthTooltipLine(tooltip, "Python", snapshot.python());
			appendHealthTooltipLine(tooltip, "pip", snapshot.pip());
			appendHealthTooltipLine(tooltip, "librosa", snapshot.librosa());
			appendHealthTooltipLine(tooltip, "Demucs", snapshot.demucs());
			appendHealthTooltipLine(tooltip, "torch", snapshot.torch());
			appendHealthTooltipLine(tooltip, "ffmpeg", snapshot.ffmpeg());
			ImGui.setTooltip(tooltip.toString());
		}
	}

	private static String buildRuntimeHealthSummary(PythonEnvironmentDiagnostics.RuntimeHealthSnapshot snapshot) {
		int issueCount = countRuntimeIssues(snapshot);
		String pythonLabel = concisePythonLabel(snapshot.python());
		if (issueCount == 0) {
			return BBTexts.get("beatblock.audio.runtime.ok", pythonLabel);
		}
		return BBTexts.get("beatblock.audio.runtime.issues", issueCount, pythonLabel, firstRuntimeIssue(snapshot));
	}

	private static int countRuntimeIssues(PythonEnvironmentDiagnostics.RuntimeHealthSnapshot snapshot) {
		int count = 0;
		count += isRuntimeIssue(snapshot.python()) ? 1 : 0;
		count += isRuntimeIssue(snapshot.pip()) ? 1 : 0;
		count += isRuntimeIssue(snapshot.librosa()) ? 1 : 0;
		count += isRuntimeIssue(snapshot.demucs()) ? 1 : 0;
		count += isRuntimeIssue(snapshot.torch()) ? 1 : 0;
		count += isRuntimeIssue(snapshot.ffmpeg()) ? 1 : 0;
		return count;
	}

	private static String firstRuntimeIssue(PythonEnvironmentDiagnostics.RuntimeHealthSnapshot snapshot) {
		PythonEnvironmentDiagnostics.HealthItem[] items = {
			snapshot.python(), snapshot.pip(), snapshot.librosa(),
			snapshot.demucs(), snapshot.torch(), snapshot.ffmpeg()
		};
		String[] labels = {"Python", "pip", "librosa", "Demucs", "torch", "ffmpeg"};
		for (int i = 0; i < items.length; i++) {
			if (isRuntimeIssue(items[i])) {
				return labels[i] + " " + conciseHealthDetail(items[i]);
			}
		}
		return BBTexts.get("beatblock.audio.runtime.see_tooltip");
	}

	private static String summaryState(PythonEnvironmentDiagnostics.RuntimeHealthSnapshot snapshot) {
		PythonEnvironmentDiagnostics.HealthItem[] items = {
			snapshot.python(), snapshot.pip(), snapshot.librosa(),
			snapshot.demucs(), snapshot.torch(), snapshot.ffmpeg()
		};
		boolean hasWarn = false;
		for (PythonEnvironmentDiagnostics.HealthItem item : items) {
			String state = item != null ? item.state() : "unknown";
			if ("error".equals(state) || "missing".equals(state)) {
				return "error";
			}
			if ("warn".equals(state) || "unknown".equals(state)) {
				hasWarn = true;
			}
		}
		return hasWarn ? "warn" : "ok";
	}

	private static ImVec4 runtimeStateColor(String state) {
		return switch (state) {
			case "ok" -> new ImVec4(0.36f, 0.79f, 0.65f, 1f);
			case "warn" -> new ImVec4(0.94f, 0.62f, 0.16f, 1f);
			case "error" -> new ImVec4(0.87f, 0.30f, 0.30f, 1f);
			default -> new ImVec4(0.62f, 0.64f, 0.70f, 1f);
		};
	}

	private static boolean isRuntimeIssue(PythonEnvironmentDiagnostics.HealthItem item) {
		if (item == null || item.state() == null) return true;
		return !"ok".equals(item.state());
	}

	private static String concisePythonLabel(PythonEnvironmentDiagnostics.HealthItem item) {
		if (item == null || item.detail() == null || item.detail().isBlank()) {
			return BBTexts.get("beatblock.audio.runtime.python_unknown");
		}
		String detail = item.detail().trim();
		int versionIndex = detail.toLowerCase().indexOf("python");
		if (versionIndex >= 0) {
			return detail.substring(versionIndex);
		}
		return BBTexts.get("beatblock.audio.runtime.python_detected");
	}

	private static String conciseHealthDetail(PythonEnvironmentDiagnostics.HealthItem item) {
		if (item == null || item.detail() == null || item.detail().isBlank()) {
			return BBTexts.get("beatblock.audio.runtime.unavailable");
		}
		String detail = item.detail().trim();
		if (detail.length() <= 28) {
			return detail;
		}
		return detail.substring(0, 28) + "…";
	}

	private static void appendHealthTooltipLine(StringBuilder tooltip, String label, PythonEnvironmentDiagnostics.HealthItem item) {
		tooltip.append('\n')
			.append(label)
			.append(": ")
			.append(item != null && item.detail() != null && !item.detail().isBlank()
				? item.detail()
				: BBTexts.get("beatblock.audio.runtime.unknown"));
	}
}
