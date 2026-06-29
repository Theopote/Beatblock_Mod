package com.beatblock.ui.panels;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.presenter.EnvironmentSetupPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

/**
 * 首次打开 BeatBlock 时的 Python / 音频分析依赖设置向导。
 */
public final class EnvironmentSetupPanel {

	private final EnvironmentSetupPresenter presenter;
	private final ImBoolean installDemucs = new ImBoolean(false);
	private boolean autoOpenTriggered;

	EnvironmentSetupPanel(EnvironmentSetupPresenter presenter) {
		this.presenter = presenter;
	}

	public EnvironmentSetupPanel() {
		this(PresenterFactories.environmentSetupPresenter());
	}

	public void open() {
		presenter.open();
	}

	public void onUiOpened() {
		if (!autoOpenTriggered) {
			autoOpenTriggered = true;
			presenter.requestAutoOpenIfNeeded();
		}
	}

	public void render() {
		onUiOpened();
		if (!presenter.isOpen()) {
			return;
		}

		ImGui.setNextWindowSize(560, 0, ImGuiCond.FirstUseEver);
		ImGui.setNextWindowPos(
			ImGui.getIO().getDisplaySizeX() * 0.5f,
			ImGui.getIO().getDisplaySizeY() * 0.35f,
			ImGuiCond.FirstUseEver,
			0.5f,
			0.35f
		);

		int flags = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.AlwaysAutoResize;

		if (!ImGui.begin(BBTexts.get("beatblock.env_setup.title"), flags)) {
			ImGui.end();
			return;
		}

		try {
			var state = presenter.viewState();
			ImGui.textWrapped(BBTexts.get("beatblock.env_setup.desc"));
			ImGui.separator();
			ImGui.spacing();

			renderHealthSummary(state);
			ImGui.spacing();

			switch (state.phase()) {
				case CHECKING -> ImGui.textDisabled(BBTexts.get("beatblock.env_setup.checking"));
				case PYTHON_MISSING -> renderPythonMissing();
				case NEEDS_SETUP, INSTALL_FAILED -> renderNeedsSetup(state);
				case INSTALLING -> renderInstalling(state);
				case READY, DONE -> renderDone(state);
			}
		} finally {
			ImGui.end();
		}
	}

	private void renderHealthSummary(EnvironmentSetupPresenter.ViewState state) {
		var health = state.health();
		if (health == null || state.phase() == EnvironmentSetupPresenter.Phase.CHECKING) {
			return;
		}
		renderHealthLine("Python", health.python());
		renderHealthLine("pip", health.pip());
		renderHealthLine("librosa", health.librosa());
		renderHealthLine("Demucs", health.demucs());
		renderHealthLine("torch", health.torch());
		renderHealthLine("ffmpeg", health.ffmpeg());
	}

	private static void renderHealthLine(String label, com.beatblock.audio.python.PythonEnvironmentDiagnostics.HealthItem item) {
		if (item == null) {
			return;
		}
		float[] color = switch (item.state()) {
			case "ok" -> new float[]{0.4f, 1f, 0.4f, 1f};
			case "missing", "error" -> new float[]{1f, 0.45f, 0.35f, 1f};
			case "warn" -> new float[]{1f, 0.75f, 0.2f, 1f};
			default -> new float[]{0.7f, 0.7f, 0.7f, 1f};
		};
		ImGui.textColored(color[0], color[1], color[2], color[3],
			label + ": " + item.detail());
	}

	private void renderPythonMissing() {
		ImGui.textWrapped(BBTexts.get("beatblock.env_setup.python_missing"));
		ImGui.spacing();
		ImGui.bulletText(BBTexts.get("beatblock.env_setup.python_hint_winget"));
		ImGui.bulletText(BBTexts.get("beatblock.env_setup.python_hint_path"));
		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.env_setup.retry") + "##envRetry")) {
			presenter.open();
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.env_setup.skip") + "##envSkipMissing")) {
			presenter.skipForNow();
		}
	}

	private void renderNeedsSetup(EnvironmentSetupPresenter.ViewState state) {
		if (state.phase() == EnvironmentSetupPresenter.Phase.INSTALL_FAILED && !state.statusMessage().isBlank()) {
			ImGui.textColored(1f, 0.45f, 0.35f, 1f, BBTexts.get("beatblock.env_setup.install_failed"));
			ImGui.textWrapped(state.statusMessage());
			ImGui.spacing();
		} else {
			ImGui.textWrapped(BBTexts.get("beatblock.env_setup.needs_setup"));
		}

		installDemucs.set(state.installDemucs());
		if (ImGui.checkbox(BBTexts.get("beatblock.env_setup.install_demucs"), installDemucs)) {
			presenter.setInstallDemucs(installDemucs.get());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.env_setup.install_demucs.tooltip"));
		}

		ImGui.spacing();
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
		if (ImGui.button(BBTexts.get("beatblock.env_setup.install") + "##envInstall", -1f, 32f)) {
			presenter.startInstall();
		}
		ImGui.popStyleColor(3);

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.env_setup.skip") + "##envSkip")) {
			presenter.skipForNow();
		}
	}

	private void renderInstalling(EnvironmentSetupPresenter.ViewState state) {
		String stepLabel = progressLabel(state.progressStep());
		ImGui.text(stepLabel);
		ImGui.progressBar(Math.max(0f, Math.min(1f, state.progressPercent() / 100f)), -1f, 0f, stepLabel);
		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##envCancelInstall")) {
			presenter.cancelInstall();
		}
	}

	private void renderDone(EnvironmentSetupPresenter.ViewState state) {
		ImGui.textColored(0.4f, 1f, 0.4f, 1f, BBTexts.get("beatblock.env_setup.done"));
		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.common.close") + "##envClose", -1f, 32f)) {
			presenter.close();
			if (state.phase() == EnvironmentSetupPresenter.Phase.DONE) {
				ToastNotificationSystem.showSuccess(BBTexts.get("beatblock.toast.env_setup.complete"));
			}
		}
	}

	private static String progressLabel(String step) {
		if (step == null || step.isBlank()) {
			return BBTexts.get("beatblock.env_setup.progress.generic");
		}
		return switch (step) {
			case "ENV_VENV" -> BBTexts.get("beatblock.env_setup.progress.venv");
			case "DEPENDENCY_INSTALL" -> BBTexts.get("beatblock.env_setup.progress.deps");
			case "DEMUCS_DEP_CHECK", "DEMUCS_DEP_INSTALL" -> BBTexts.get("beatblock.env_setup.progress.demucs");
			default -> BBTexts.get("beatblock.env_setup.progress.generic");
		};
	}
}
