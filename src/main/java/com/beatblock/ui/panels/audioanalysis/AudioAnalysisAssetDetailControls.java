package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.audio.assets.AudioAnalysisMode;
import com.beatblock.audio.assets.AudioAnalysisStep;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import imgui.ImGui;
import imgui.ImVec4;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;

/** 右侧资产详情面板。 */
final class AudioAnalysisAssetDetailControls {

	private AudioAnalysisAssetDetailControls() {
	}

	static void renderDetailPanel(AudioAnalysisPanelHost host, AudioAsset asset) {
		AudioAnalysisPanelUiState state = host.uiState();
		IconButtonStyle.pushBeatBlockIconButton();
		if (ImGui.button(AudioAnalysisPanelImGui.iconLabel(Icons.Layout.LEFT_COLLAPSE, "<") + "##collapse",
			AudioAnalysisPanelImGui.ICON_BTN, AudioAnalysisPanelImGui.ICON_BTN)) {
			state.setDetailExpanded(false);
		}
		IconButtonStyle.popBeatBlockIconButton();
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.audio.collapse_detail"));
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.audio.reset_ratio") + "##resetDetailRatio")) {
			state.setDetailRatio(0.50f);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.audio.reset_ratio.tooltip"));
		ImGui.sameLine();
		ImGui.text(BBTexts.get("beatblock.audio.detail"));
		ImGui.separator();

		if (asset == null) {
			ImGui.spacing();
			ImGui.textDisabled(BBTexts.get("beatblock.audio.select_asset_hint"));
			return;
		}

		switch (asset.getStatus()) {
			case COMPLETED -> renderDetailCompleted(host, asset);
			case QUEUED -> renderDetailQueued(host, asset);
			case ANALYZING -> renderDetailAnalyzing(state, asset);
			case PENDING -> renderDetailPending(state, asset);
			case FAILED -> renderDetailFailed(state, asset);
		}
	}

	private static void renderDetailQueued(AudioAnalysisPanelHost host, AudioAsset asset) {
		AudioAnalysisPanelUiState state = host.uiState();
		AudioAssetManager manager = AudioAssetManager.getInstance();
		int pos = manager.getQueuePosition(asset.getId());
		if (AudioAnalysisPanelImGui.beginDetailSection("queued_status", BBTexts.get("beatblock.audio.queue_status"), false)) {
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.file_name"), asset.getFileName());
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.status_label"), BBTexts.get("beatblock.audio.queued"));
			if (pos > 0) {
				AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.queue_position"), "#" + pos, new ImVec4(0.95f, 0.78f, 0.38f, 1f));
			}
			AudioAnalysisPanelImGui.compactGap();
			AudioAnalysisPanelImGui.textDisabledWrapped(BBTexts.get("beatblock.audio.queue_hint"));
			AudioAnalysisPanelImGui.textDisabledWrapped(BBTexts.get("beatblock.audio.queue_drag_hint"));
			AudioAnalysisPanelImGui.endDetailSection();
		}

		if (AudioAnalysisPanelImGui.beginDetailSection("queued_actions", BBTexts.get("beatblock.audio.actions"), true)) {
			boolean canMoveUp = manager.canMoveQueueUp(asset.getId());
			boolean canMoveDown = manager.canMoveQueueDown(asset.getId());
			float half = (ImGui.getContentRegionAvailX() - 6f) * 0.5f;
			if (canMoveUp) {
				if (ImGui.button(BBTexts.get("beatblock.audio.move_up") + "##detailQueueUp", half, 26f)) {
					manager.moveQueueUp(asset.getId());
				}
			} else {
				ImGui.beginDisabled();
				ImGui.button(BBTexts.get("beatblock.audio.move_up") + "##detailQueueUpDisabled", half, 26f);
				ImGui.endDisabled();
			}
			ImGui.sameLine();
			if (canMoveDown) {
				if (ImGui.button(BBTexts.get("beatblock.audio.move_down") + "##detailQueueDown", half, 26f)) {
					manager.moveQueueDown(asset.getId());
				}
			} else {
				ImGui.beginDisabled();
				ImGui.button(BBTexts.get("beatblock.audio.move_down") + "##detailQueueDownDisabled", half, 26f);
				ImGui.endDisabled();
			}
			AudioAnalysisPanelImGui.compactGap();
			if (ImGui.button(BBTexts.get("beatblock.audio.remove_from_queue") + "##detailRemoveQueued", ImGui.getContentRegionAvailX(), 26f)) {
				manager.remove(asset.getId());
				state.clearSelectedAssetIfMatches(asset);
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}
	}

	private static void renderDetailCompleted(AudioAnalysisPanelHost host, AudioAsset asset) {
		AudioAnalysisPanelUiState state = host.uiState();
		Beatmap detailBm = asset.getBeatmap();
		boolean hasStemSeparation = detailBm != null
			&& detailBm.meta != null
			&& detailBm.meta.hasStemSeparation();

		if (AudioAnalysisPanelImGui.beginDetailSection("completed_basic", BBTexts.get("beatblock.audio.basic_info"), false)) {
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.file_name"), asset.getFileName());
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.duration"),
				BBTexts.get("beatblock.audio.duration_seconds", asset.getDurationSeconds()));
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.sample_rate"), asset.getSampleRate() + " Hz");
			AudioAnalysisPanelImGui.endDetailSection();
		}

		if (AudioAnalysisPanelImGui.beginDetailSection("completed_result", BBTexts.get("beatblock.audio.analysis_result"), false)) {
			AudioAnalysisPanelImGui.detailRowCompact(state, "BPM", String.format("%.1f", asset.getBpm()), AudioAnalysisPanelImGui.COLOR_PROGRESS_FG);
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.time_signature"), "4/4");
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.analysis_mode"),
				hasStemSeparation ? BBTexts.get("beatblock.audio.demucs_mode") : BBTexts.get("beatblock.audio.basic_mode"));
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.requested_mode"),
				AudioAnalysisPanelImGui.analysisModeLabel(asset.getRequestedAnalysisMode()));
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.resolved_mode"),
				AudioAnalysisPanelImGui.analysisModeLabel(asset.getResolvedAnalysisMode()));
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.cache_source"),
				AudioAnalysisPanelImGui.cacheSourceLabel(asset.getCacheSource()));
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.beat_count"),
				BBTexts.get("beatblock.audio.beats_unit", asset.getBeatCount()), AudioAnalysisPanelImGui.COLOR_MID);
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.section_count"),
				BBTexts.get("beatblock.audio.sections_unit", asset.getSectionCount()));
			if (asset.getRequestedAnalysisMode() == AudioAnalysisMode.DEMUCS
				&& asset.getResolvedAnalysisMode() == AudioAnalysisMode.BASIC) {
				AudioAnalysisPanelImGui.compactGap();
				AudioAnalysisPanelImGui.renderWarningBanner();
			}
			AudioAnalysisPanelImGui.compactGap();
			AudioAnalysisPanelImGui.renderCacheBadge(asset.getCacheSource());
			if (asset.getInfoMessage() != null && !asset.getInfoMessage().isBlank()) {
				AudioAnalysisPanelImGui.compactGap();
				AudioAnalysisPanelImGui.textDisabledWrapped(asset.getInfoMessage());
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}

		if (AudioAnalysisPanelImGui.beginDetailSection("completed_waveform", BBTexts.get("beatblock.audio.waveform_preview"), false)) {
			AudioAnalysisPanelImGui.renderWaveformPreview(detailBm != null ? detailBm.waveformPreview : null);
			AudioAnalysisPanelImGui.endDetailSection();
		}

		if (AudioAnalysisPanelImGui.beginDetailSection("completed_distribution",
			hasStemSeparation ? BBTexts.get("beatblock.audio.stem_tracks") : BBTexts.get("beatblock.audio.band_distribution"), false)) {
			if (!hasStemSeparation) {
				AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.low_band"),
					BBTexts.get("beatblock.audio.beats_unit", asset.getLowCount()), AudioAnalysisPanelImGui.COLOR_LOW);
				AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.mid_band"),
					BBTexts.get("beatblock.audio.beats_unit", asset.getMidCount()), AudioAnalysisPanelImGui.COLOR_MID);
				AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.high_band"),
					BBTexts.get("beatblock.audio.beats_unit", asset.getHighCount()), AudioAnalysisPanelImGui.COLOR_HIGH);
				AudioAnalysisPanelImGui.compactGap();
				AudioAnalysisPanelImGui.renderBandBar(asset);
			} else {
				AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.stem.drums"),
					AudioAnalysisPanelImGui.stemStateLabel(detailBm, "drums"), new ImVec4(0.87f, 0.53f, 0.25f, 1f));
				AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.stem.bass"),
					AudioAnalysisPanelImGui.stemStateLabel(detailBm, "bass"), new ImVec4(0.27f, 0.60f, 0.87f, 1f));
				AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.stem.vocals"),
					AudioAnalysisPanelImGui.stemStateLabel(detailBm, "vocals"), new ImVec4(0.67f, 0.38f, 0.84f, 1f));
				AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.stem.other"),
					AudioAnalysisPanelImGui.stemStateLabel(detailBm, "other"), new ImVec4(0.58f, 0.72f, 0.30f, 1f));
				AudioAnalysisPanelImGui.compactGap();
				AudioAnalysisPanelImGui.textDisabledWrapped(BBTexts.get("beatblock.audio.stem_mute_hint"));
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}

		if (AudioAnalysisPanelImGui.beginDetailSection("completed_demucs", BBTexts.get("beatblock.audio.demucs_section"), false)) {
			if (hasStemSeparation) {
				AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.status"),
					BBTexts.get("beatblock.audio.generated"), new ImVec4(0.36f, 0.79f, 0.65f, 1f));
				String separationMode = detailBm.meta.separationMode();
				AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.separation_mode"),
					separationMode != null && !separationMode.isBlank() ? separationMode : "demucs",
					new ImVec4(0.22f, 0.78f, 0.82f, 1f));
				AudioAnalysisPanelImGui.renderStemDetailRow(state, host, detailBm, "drums", BBTexts.get("beatblock.audio.stem.drums"), new ImVec4(0.87f, 0.53f, 0.25f, 1f));
				AudioAnalysisPanelImGui.renderStemDetailRow(state, host, detailBm, "bass", BBTexts.get("beatblock.audio.stem.bass"), new ImVec4(0.27f, 0.60f, 0.87f, 1f));
				AudioAnalysisPanelImGui.renderStemDetailRow(state, host, detailBm, "vocals", BBTexts.get("beatblock.audio.stem.vocals"), new ImVec4(0.67f, 0.38f, 0.84f, 1f));
				AudioAnalysisPanelImGui.renderStemDetailRow(state, host, detailBm, "other", BBTexts.get("beatblock.audio.stem.other"), new ImVec4(0.58f, 0.72f, 0.30f, 1f));
			} else {
				AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.status"),
					BBTexts.get("beatblock.audio.not_generated_basic"), new ImVec4(0.94f, 0.62f, 0.16f, 1f));
				AudioAnalysisPanelImGui.compactGap();
				AudioAnalysisPanelImGui.textDisabledWrapped(BBTexts.get("beatblock.audio.demucs_fallback_detail"));
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}

		if (AudioAnalysisPanelImGui.beginDetailSection("completed_actions", BBTexts.get("beatblock.audio.actions"), true)) {
			float actionW = (ImGui.getContentRegionAvailX() - 6f) * 0.5f;
			if (ImGui.button(BBTexts.get("beatblock.audio.reanalyze_demucs") + "##detailReanalyzeDemucs", actionW, 26f)) {
				String result = AudioAssetManager.getInstance().clearCacheAndReanalyze(asset, AudioAnalysisMode.DEMUCS);
				state.setPanelHint(result, result.contains("未初始化") || result.contains("无效"));
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.audio.reanalyze_demucs.tooltip"));
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.audio.reanalyze_basic") + "##detailReanalyzeBasic", actionW, 26f)) {
				String result = AudioAssetManager.getInstance().clearCacheAndReanalyze(asset, AudioAnalysisMode.BASIC);
				state.setPanelHint(result, result.contains("未初始化") || result.contains("无效"));
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.audio.reanalyze_basic.tooltip"));
			}

			AudioAnalysisPanelImGui.compactGap();
			AudioAnalysisMode compareMode = asset.getResolvedAnalysisMode() == AudioAnalysisMode.DEMUCS
				? AudioAnalysisMode.BASIC
				: AudioAnalysisMode.DEMUCS;
			String compareLabel = compareMode == AudioAnalysisMode.DEMUCS
				? BBTexts.get("beatblock.audio.compare_demucs") + "##detailCompareDemucs"
				: BBTexts.get("beatblock.audio.compare_basic") + "##detailCompareBasic";
			if (ImGui.button(compareLabel, ImGui.getContentRegionAvailX(), 24f)) {
				String result = AudioAssetManager.getInstance().clearCacheAndReanalyze(asset, compareMode);
				state.setPanelHint(result, result.contains("未初始化") || result.contains("无效"));
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(compareMode == AudioAnalysisMode.DEMUCS
					? BBTexts.get("beatblock.audio.compare_demucs.tooltip")
					: BBTexts.get("beatblock.audio.compare_basic.tooltip"));
			}

			AudioAnalysisPanelImGui.compactGap();
			float btnW = ImGui.getContentRegionAvailX();
			ImGui.pushStyleColor(ImGuiCol.Button, 0.28f, 0.26f, 0.45f, 1f);
			ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.35f, 0.33f, 0.55f, 1f);
			ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.45f, 0.43f, 0.65f, 1f);
			ImGui.button(Icons.MENU + "  " + BBTexts.get("beatblock.audio.drag_timeline_btn") + "##dragBtn", btnW, 28f);
			ImGui.popStyleColor(3);

			if (ImGui.beginDragDropSource(imgui.flag.ImGuiDragDropFlags.SourceAllowNullID)) {
				AudioAssetManager.getInstance().setCurrentDragAsset(asset);
				ImGui.setDragDropPayload(
					"BB_AUDIO_ASSET_ID",
					asset.getId().getBytes(),
					ImGuiCond.Once
				);
				ImGui.text(Icons.MUSIC_NOTE + " " + asset.getFileName());
				ImGui.textDisabled(String.format("%.1f BPM · %s",
					asset.getBpm(), BBTexts.get("beatblock.audio.beats_short", asset.getBeatCount())));
				ImGui.endDragDropSource();
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}
	}

	private static void renderDetailAnalyzing(AudioAnalysisPanelUiState state, AudioAsset asset) {
		float progress = AudioAnalysisPanelImGui.computeProgress(asset);
		String statusText = asset.getProcessingStatusText();

		if (AudioAnalysisPanelImGui.beginDetailSection("analyzing_progress", BBTexts.get("beatblock.audio.analyzing_progress"), false)) {
			ImGui.pushStyleColor(ImGuiCol.PlotHistogram,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.x,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.y,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.z,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.w);
			ImGui.pushStyleColor(ImGuiCol.FrameBg,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_BG.x,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_BG.y,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_BG.z,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_BG.w);
			ImGui.progressBar(progress, -1f, 0f, "");
			ImGui.popStyleColor(2);
			ImGui.textDisabled(String.format("%.0f%%", progress * 100f));

			if (statusText != null && !statusText.isBlank()) {
				AudioAnalysisPanelImGui.compactGap();
				ImGui.pushStyleColor(ImGuiCol.Text,
					AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.x,
					AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.y,
					AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.z,
					AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.w);
				ImGui.textWrapped(BBTexts.get("beatblock.audio.processing", statusText));
				ImGui.popStyleColor();
				ImGui.textDisabled(BBTexts.get("beatblock.audio.current_phase", AudioAnalysisPanelImGui.analysisPhaseLabel(asset)));
			}

			if (asset.getInfoMessage() != null && !asset.getInfoMessage().isBlank()) {
				AudioAnalysisPanelImGui.compactGap();
				AudioAnalysisPanelImGui.textDisabledWrapped(asset.getInfoMessage());
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}

		if (statusText != null && !statusText.isBlank()) {
			return;
		}

		if (AudioAnalysisPanelImGui.beginDetailSection("analyzing_steps", BBTexts.get("beatblock.audio.steps_detail"), false)) {
			for (AudioAnalysisStep step : AudioAnalysisStep.values()) {
				boolean done = asset.getFinishedSteps().contains(step);
				boolean active = AudioAnalysisPanelImGui.isActiveStep(asset, step);
				String label = AudioAnalysisPanelImGui.stepLabel(step);
				if (done) {
					ImGui.pushStyleColor(ImGuiCol.Text,
						AudioAnalysisPanelImGui.COLOR_MID.x,
						AudioAnalysisPanelImGui.COLOR_MID.y,
						AudioAnalysisPanelImGui.COLOR_MID.z,
						AudioAnalysisPanelImGui.COLOR_MID.w);
					ImGui.text(Icons.CHECK + "  " + label);
					ImGui.popStyleColor();
				} else if (active) {
					ImGui.pushStyleColor(ImGuiCol.Text,
						AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.x,
						AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.y,
						AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.z,
						AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.w);
					ImGui.text("▷  " + label + "...");
					ImGui.popStyleColor();
				} else {
					ImGui.textDisabled("     " + label);
				}
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}
	}

	private static void renderDetailPending(AudioAnalysisPanelUiState state, AudioAsset asset) {
		if (AudioAnalysisPanelImGui.beginDetailSection("pending_basic", BBTexts.get("beatblock.audio.basic_info"), false)) {
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.file_name"), asset.getFileName());
			AudioAnalysisPanelImGui.detailRowCompact(state, BBTexts.get("beatblock.audio.duration"),
				BBTexts.get("beatblock.audio.duration_seconds", asset.getDurationSeconds()));
			AudioAnalysisPanelImGui.compactGap();
			ImGui.textDisabled(BBTexts.get("beatblock.audio.pending_hint"));
			AudioAnalysisPanelImGui.endDetailSection();
		}
		if (AudioAnalysisPanelImGui.beginDetailSection("pending_actions", BBTexts.get("beatblock.audio.actions"), true)) {
			if (ImGui.button(BBTexts.get("beatblock.audio.start_analyze") + "##detailAnalyze", ImGui.getContentRegionAvailX(), 26f)) {
				AudioAssetManager.getInstance().startAnalysis(asset);
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}
	}

	private static void renderDetailFailed(AudioAnalysisPanelUiState state, AudioAsset asset) {
		if (AudioAnalysisPanelImGui.beginDetailSection("failed_error", BBTexts.get("beatblock.audio.error_section"), false)) {
			ImGui.pushStyleColor(ImGuiCol.Text, 0.87f, 0.30f, 0.30f, 1f);
			ImGui.textWrapped(asset.getErrorMessage() != null
				? asset.getErrorMessage()
				: BBTexts.get("beatblock.audio.unknown_error"));
			ImGui.popStyleColor();
			if (asset.getInfoMessage() != null && !asset.getInfoMessage().isBlank()) {
				AudioAnalysisPanelImGui.compactGap();
				AudioAnalysisPanelImGui.textDisabledWrapped(asset.getInfoMessage());
			}
			AudioAnalysisPanelImGui.compactGap();
			ImGui.textDisabled(BBTexts.get("beatblock.audio.supported_formats"));
			AudioAnalysisPanelImGui.endDetailSection();
		}
		if (AudioAnalysisPanelImGui.beginDetailSection("failed_actions", BBTexts.get("beatblock.audio.actions"), true)) {
			if (ImGui.button(BBTexts.get("beatblock.audio.convert_mp3_one_click") + "##detailConvert", ImGui.getContentRegionAvailX(), 26f)) {
				boolean accepted = AudioAssetManager.getInstance().requestConvertToMp3(asset);
				if (!accepted) {
					asset.setErrorMessage(BBTexts.get("beatblock.audio.convert_not_implemented"));
				}
			}
			AudioAnalysisPanelImGui.compactGap();
			if (ImGui.button(BBTexts.get("beatblock.audio.retry") + "##detailRetry", ImGui.getContentRegionAvailX(), 26f)) {
				AudioAssetManager.getInstance().startAnalysis(asset, asset.getRequestedAnalysisMode());
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}
	}
}
