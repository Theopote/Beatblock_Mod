package com.beatblock.ui.presenter;

import com.beatblock.audio.AnalysisCancelControl;
import com.beatblock.audio.AnalyzerInstaller;
import com.beatblock.audio.python.PythonEnvironmentDiagnostics;
import com.beatblock.ui.preferences.UiPreferences;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 首次打开 BeatBlock 时的 Python 环境检测与依赖安装逻辑。
 */
public final class EnvironmentSetupPresenter {

	public enum Phase {
		CHECKING,
		NEEDS_SETUP,
		PYTHON_MISSING,
		READY,
		INSTALLING,
		INSTALL_FAILED,
		DONE
	}

	public record ViewState(
		Phase phase,
		String statusMessage,
		String progressStep,
		int progressPercent,
		boolean installDemucs,
		PythonEnvironmentDiagnostics.RuntimeHealthSnapshot health
	) {
		public static ViewState checking() {
			return new ViewState(
				Phase.CHECKING,
				"",
				"",
				0,
				false,
				PythonEnvironmentDiagnostics.RuntimeHealthSnapshot.empty()
			);
		}
	}

	private final PythonEnvironmentDiagnostics diagnostics;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "beatblock-env-setup");
		t.setDaemon(true);
		return t;
	});

	private volatile ViewState viewState = ViewState.checking();
	private final AtomicBoolean checkInFlight = new AtomicBoolean();
	private final AtomicBoolean installInFlight = new AtomicBoolean();
	private volatile AnalysisCancelControl installControl = new AnalysisCancelControl();
	private boolean openRequested;
	private boolean manualOpen;

	EnvironmentSetupPresenter(PythonEnvironmentDiagnostics diagnostics) {
		this.diagnostics = diagnostics;
	}

	public ViewState viewState() {
		return viewState;
	}

	public boolean shouldAutoOpen() {
		return !UiPreferences.isPythonSetupAcknowledged();
	}

	public void requestAutoOpenIfNeeded() {
		if (!shouldAutoOpen()) {
			return;
		}
		manualOpen = false;
		openRequested = true;
		startEnvironmentCheck();
	}

	public void invalidateProbeCache() {
		diagnostics.clearProbeCache();
	}

	public void open() {
		invalidateProbeCache();
		manualOpen = true;
		openRequested = true;
		startEnvironmentCheck();
	}

	public boolean isOpen() {
		return openRequested;
	}

	public void close() {
		openRequested = false;
		manualOpen = false;
		installControl.cancelRunningProcess();
	}

	public void skipForNow() {
		UiPreferences.setPythonSetupAcknowledged(true);
		close();
	}

	public void setInstallDemucs(boolean installDemucs) {
		viewState = new ViewState(
			viewState.phase(),
			viewState.statusMessage(),
			viewState.progressStep(),
			viewState.progressPercent(),
			installDemucs,
			viewState.health()
		);
	}

	public void startInstall() {
		if (installInFlight.get()) {
			return;
		}
		Path configDir = diagnostics.beatblockConfigDirOrNull();
		if (configDir == null) {
			viewState = withPhase(Phase.INSTALL_FAILED, "无法定位 config/beatblock 目录");
			return;
		}

		installInFlight.set(true);
		installControl = new AnalysisCancelControl();
		viewState = new ViewState(
			Phase.INSTALLING,
			"",
			"ENV_VENV",
			0,
			viewState.installDemucs(),
			viewState.health()
		);

		boolean installDemucs = viewState.installDemucs();
		executor.submit(() -> {
			try {
				AnalyzerInstaller.ensureInstalled();
				String error = diagnostics.setupAnalyzerEnvironment(
					configDir,
					installDemucs,
					installControl,
					(step, percent) -> viewState = new ViewState(
						Phase.INSTALLING,
						"",
						step,
						percent,
						installDemucs,
						viewState.health()
					)
				);
				if (error != null) {
					viewState = withPhase(Phase.INSTALL_FAILED, error);
					return;
				}
				refreshHealthSnapshot(configDir);
				viewState = withPhase(Phase.DONE, "");
				UiPreferences.setPythonSetupAcknowledged(true);
			} catch (Exception e) {
				viewState = withPhase(Phase.INSTALL_FAILED, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
			} finally {
				installInFlight.set(false);
			}
		});
	}

	public void cancelInstall() {
		installControl.cancelRunningProcess();
		if (viewState.phase() == Phase.INSTALLING) {
			viewState = withPhase(Phase.NEEDS_SETUP, "");
		}
		installInFlight.set(false);
	}

	private void startEnvironmentCheck() {
		if (!checkInFlight.compareAndSet(false, true)) {
			return;
		}
		viewState = ViewState.checking();
		executor.submit(() -> {
			try {
				Path configDir = diagnostics.beatblockConfigDirOrNull();
				if (configDir == null) {
					viewState = withPhase(Phase.PYTHON_MISSING, "");
					return;
				}
				try {
					AnalyzerInstaller.ensureInstalled();
				} catch (AnalyzerInstaller.AnalyzerInstallException e) {
					viewState = withPhase(Phase.INSTALL_FAILED, "分析脚本解压失败：" + e.getMessage());
					return;
				}

				PythonEnvironmentDiagnostics.RuntimeHealthSnapshot health =
					diagnostics.probeRuntimeHealth(configDir);
				viewState = new ViewState(
					viewState.phase(),
					viewState.statusMessage(),
					viewState.progressStep(),
					viewState.progressPercent(),
					viewState.installDemucs(),
					health
				);

				if (isEnvironmentReady(health)) {
					UiPreferences.setPythonSetupAcknowledged(true);
					if (!manualOpen) {
						openRequested = false;
					}
					viewState = withPhase(Phase.READY, "");
					return;
				}

				if (isPythonMissing(health)) {
					viewState = withPhase(Phase.PYTHON_MISSING, "");
					return;
				}

				viewState = withPhase(Phase.NEEDS_SETUP, "");
			} finally {
				checkInFlight.set(false);
			}
		});
	}

	private void refreshHealthSnapshot(Path configDir) {
		PythonEnvironmentDiagnostics.RuntimeHealthSnapshot health = diagnostics.probeRuntimeHealth(configDir);
		viewState = new ViewState(
			viewState.phase(),
			viewState.statusMessage(),
			viewState.progressStep(),
			viewState.progressPercent(),
			viewState.installDemucs(),
			health
		);
	}

	private static boolean isEnvironmentReady(PythonEnvironmentDiagnostics.RuntimeHealthSnapshot health) {
		return isOk(health.python())
			&& isOk(health.pip())
			&& isOk(health.librosa());
	}

	private static boolean isPythonMissing(PythonEnvironmentDiagnostics.RuntimeHealthSnapshot health) {
		return health.python().state().equals("missing") || health.python().state().equals("error");
	}

	private static boolean isOk(PythonEnvironmentDiagnostics.HealthItem item) {
		return item != null && "ok".equals(item.state());
	}

	private ViewState withPhase(Phase phase, String message) {
		return new ViewState(
			phase,
			message != null ? message : "",
			viewState.progressStep(),
			viewState.progressPercent(),
			viewState.installDemucs(),
			viewState.health()
		);
	}

	void shutdown() {
		installControl.cancelRunningProcess();
		executor.shutdownNow();
	}
}
