package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.ui.presenter.AudioAnalysisPanelPresenter;

/** 音频解析面板宿主回调（路径导入、文件选择）。 */
public interface AudioAnalysisPanelHost {

	AudioAnalysisPanelPresenter presenter();

	AudioAnalysisPanelUiState uiState();

	boolean handleIncomingAudioPath(String path);

	String chooseAudioFilePath();
}
