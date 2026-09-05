package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.ui.presenter.AudioAnalysisPanelPresenter;

/** 音频解析面板宿主回调（路径导入、文件选择、应用后导航）。 */
public interface AudioAnalysisPanelHost {

	AudioAnalysisPanelPresenter presenter();

	AudioAnalysisPanelUiState uiState();

	boolean handleIncomingAudioPath(String path);

	String chooseAudioFilePath();

	/** 成功应用到时间线后展示时间线面板（可由 UIManager 注入）。 */
	default void showTimelineAfterApply() {
	}
}
