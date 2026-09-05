package com.beatblock.ui.panels;

import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.panels.audioanalysis.AudioAnalysisPanelHost;
import com.beatblock.ui.panels.audioanalysis.AudioAnalysisPanelRenderer;
import com.beatblock.ui.panels.audioanalysis.AudioAnalysisPanelUiState;
import com.beatblock.ui.presenter.AudioAnalysisPanelPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

/**
 * 音频解析面板 / 媒体箱
 *
 * <p>布局：左侧列表 | 右侧详情（可折叠）</p>
 */
public final class AudioAnalysisPanel implements AudioAnalysisPanelHost {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private final AudioAnalysisPanelPresenter presenter;
	private final AudioAnalysisPanelUiState uiState = new AudioAnalysisPanelUiState();
	private final Runnable showTimelineAfterApply;

	public AudioAnalysisPanel() {
		this(PresenterFactories.audioAnalysisPanelPresenter(), () -> {});
	}

	public AudioAnalysisPanel(Runnable showTimelineAfterApply) {
		this(PresenterFactories.audioAnalysisPanelPresenter(), showTimelineAfterApply);
	}

	AudioAnalysisPanel(AudioAnalysisPanelPresenter presenter) {
		this(presenter, () -> {});
	}

	AudioAnalysisPanel(AudioAnalysisPanelPresenter presenter, Runnable showTimelineAfterApply) {
		this.presenter = presenter;
		this.showTimelineAfterApply = showTimelineAfterApply != null ? showTimelineAfterApply : () -> {};
	}

	@Override
	public AudioAnalysisPanelPresenter presenter() {
		return presenter;
	}

	@Override
	public AudioAnalysisPanelUiState uiState() {
		return uiState;
	}

	@Override
	public void showTimelineAfterApply() {
		showTimelineAfterApply.run();
	}

    public void render(ImBoolean pOpen) {
        if (!pOpen.get()) {
            BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.audioAnalysisWindow());
            return;
        }
		ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding,
			AudioAnalysisPanelRenderer.outerPaddingX(),
			AudioAnalysisPanelRenderer.outerPaddingY());
        if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.audioAnalysisWindow(), pOpen, WINDOW_FLAGS)) {
            ImGui.popStyleVar();
            return;
        }
        ImGui.popStyleVar();
        try {
			AudioAnalysisPanelRenderer.renderContent(this);
        } finally {
            BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.audioAnalysisWindow());
        }
    }

	@Override
	public boolean handleIncomingAudioPath(String path) {
		var outcome = presenter.importAndAnalyze(path);
		if (outcome.ok() && outcome.asset() != null) {
			uiState.setSelectedAsset(outcome.asset());
			uiState.setPanelHint(outcome.message(), false);
			return true;
		}
		uiState.setPanelHint(outcome.message(), true);
		return false;
	}

	@Override
	public String chooseAudioFilePath() {
		return AudioAnalysisPanelRenderer.chooseFilePath(this);
    }
}
