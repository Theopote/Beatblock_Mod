package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.audio.assets.AudioAsset;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.HashSet;
import java.util.Set;

/** 音频解析面板可变 UI 状态（选中项、布局比例、提示文案等）。 */
public final class AudioAnalysisPanelUiState {

	private AudioAsset selectedAsset;
	private boolean detailExpanded = true;
	private float detailRatio = 0.50f;
	private String panelHintText;
	private boolean panelHintError;
	private long panelHintExpireAtMs;
	private final ImString importPath = new ImString(512);
	private final ImBoolean demucsToggle = new ImBoolean(false);
	private final Set<String> expandedDetailRows = new HashSet<>();

	public AudioAsset selectedAsset() {
		return selectedAsset;
	}

	public void setSelectedAsset(AudioAsset selectedAsset) {
		this.selectedAsset = selectedAsset;
	}

	public void clearSelectedAssetIfMatches(AudioAsset asset) {
		if (selectedAsset != null && asset != null && selectedAsset.getId().equals(asset.getId())) {
			selectedAsset = null;
		}
	}

	public void clearSelectedAssetIfCompleted() {
		if (selectedAsset != null && selectedAsset.getStatus() == com.beatblock.audio.assets.AudioAssetStatus.COMPLETED) {
			selectedAsset = null;
		}
	}

	public boolean detailExpanded() {
		return detailExpanded;
	}

	public void setDetailExpanded(boolean detailExpanded) {
		this.detailExpanded = detailExpanded;
	}

	public void toggleDetailExpanded() {
		detailExpanded = !detailExpanded;
	}

	public float detailRatio() {
		return detailRatio;
	}

	public void setDetailRatio(float detailRatio) {
		this.detailRatio = detailRatio;
	}

	public ImString importPath() {
		return importPath;
	}

	public ImBoolean demucsToggle() {
		return demucsToggle;
	}

	public Set<String> expandedDetailRows() {
		return expandedDetailRows;
	}

	public String panelHintText() {
		prunePanelHint();
		return panelHintText;
	}

	public boolean panelHintError() {
		return panelHintError;
	}

	public boolean hasPanelHint() {
		String text = panelHintText();
		return text != null && !text.isBlank();
	}

	public void setPanelHint(String text, boolean isError) {
		panelHintText = text;
		panelHintError = isError;
		panelHintExpireAtMs = System.currentTimeMillis() + 5000L;
	}

	public void prunePanelHint() {
		if (panelHintText == null) return;
		if (System.currentTimeMillis() >= panelHintExpireAtMs) {
			panelHintText = null;
			panelHintError = false;
		}
	}
}
