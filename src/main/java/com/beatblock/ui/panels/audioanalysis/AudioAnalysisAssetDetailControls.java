package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.audio.assets.AudioAnalysisMode;
import com.beatblock.audio.assets.AudioAnalysisStep;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.audio.beatmap.Beatmap;
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
		if (ImGui.isItemHovered()) ImGui.setTooltip("折叠详情");
		ImGui.sameLine();
		if (ImGui.button("重置比例 5:5##resetDetailRatio")) {
			state.setDetailRatio(0.50f);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip("将左右区域恢复为默认 5:5");
		ImGui.sameLine();
		ImGui.text("详情");
		ImGui.separator();

		if (asset == null) {
			ImGui.spacing();
			ImGui.textDisabled("点击左侧列表中的\n音频查看详情");
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
		if (AudioAnalysisPanelImGui.beginDetailSection("queued_status", "队列状态", false)) {
			AudioAnalysisPanelImGui.detailRowCompact(state, "文件名", asset.getFileName());
			AudioAnalysisPanelImGui.detailRowCompact(state, "当前状态", "排队中");
			if (pos > 0) {
				AudioAnalysisPanelImGui.detailRowCompact(state, "队列位次", "#" + pos, new ImVec4(0.95f, 0.78f, 0.38f, 1f));
			}
			AudioAnalysisPanelImGui.compactGap();
			AudioAnalysisPanelImGui.textDisabledWrapped("当前分析器为串行执行，前序任务完成后将自动开始。你可以继续添加文件，系统会按顺序处理。");
			AudioAnalysisPanelImGui.textDisabledWrapped("提示：左侧列表支持拖动队列项直接改顺序。");
			AudioAnalysisPanelImGui.endDetailSection();
		}

		if (AudioAnalysisPanelImGui.beginDetailSection("queued_actions", "操作", true)) {
			boolean canMoveUp = manager.canMoveQueueUp(asset.getId());
			boolean canMoveDown = manager.canMoveQueueDown(asset.getId());
			float half = (ImGui.getContentRegionAvailX() - 6f) * 0.5f;
			if (canMoveUp) {
				if (ImGui.button("上移优先级##detailQueueUp", half, 26f)) {
					manager.moveQueueUp(asset.getId());
				}
			} else {
				ImGui.beginDisabled();
				ImGui.button("上移优先级##detailQueueUpDisabled", half, 26f);
				ImGui.endDisabled();
			}
			ImGui.sameLine();
			if (canMoveDown) {
				if (ImGui.button("下移优先级##detailQueueDown", half, 26f)) {
					manager.moveQueueDown(asset.getId());
				}
			} else {
				ImGui.beginDisabled();
				ImGui.button("下移优先级##detailQueueDownDisabled", half, 26f);
				ImGui.endDisabled();
			}
			AudioAnalysisPanelImGui.compactGap();
			if (ImGui.button("移除队列项##detailRemoveQueued", ImGui.getContentRegionAvailX(), 26f)) {
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

		if (AudioAnalysisPanelImGui.beginDetailSection("completed_basic", "基本信息", false)) {
			AudioAnalysisPanelImGui.detailRowCompact(state, "文件名", asset.getFileName());
			AudioAnalysisPanelImGui.detailRowCompact(state, "时长", String.format("%.1f 秒", asset.getDurationSeconds()));
			AudioAnalysisPanelImGui.detailRowCompact(state, "采样率", asset.getSampleRate() + " Hz");
			AudioAnalysisPanelImGui.endDetailSection();
		}

		if (AudioAnalysisPanelImGui.beginDetailSection("completed_result", "解析结果", false)) {
			AudioAnalysisPanelImGui.detailRowCompact(state, "BPM", String.format("%.1f", asset.getBpm()), AudioAnalysisPanelImGui.COLOR_PROGRESS_FG);
			AudioAnalysisPanelImGui.detailRowCompact(state, "拍号", "4/4");
			AudioAnalysisPanelImGui.detailRowCompact(state, "解析模式", hasStemSeparation ? "Demucs 语义茎分离" : "传统频段分离");
			AudioAnalysisPanelImGui.detailRowCompact(state, "请求模式", AudioAnalysisPanelImGui.analysisModeLabel(asset.getRequestedAnalysisMode()));
			AudioAnalysisPanelImGui.detailRowCompact(state, "实际模式", AudioAnalysisPanelImGui.analysisModeLabel(asset.getResolvedAnalysisMode()));
			AudioAnalysisPanelImGui.detailRowCompact(state, "缓存来源", AudioAnalysisPanelImGui.cacheSourceLabel(asset.getCacheSource()));
			AudioAnalysisPanelImGui.detailRowCompact(state, "踩点数量", asset.getBeatCount() + " 个", AudioAnalysisPanelImGui.COLOR_MID);
			AudioAnalysisPanelImGui.detailRowCompact(state, "识别段落", asset.getSectionCount() + " 段");
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

		if (AudioAnalysisPanelImGui.beginDetailSection("completed_distribution", hasStemSeparation ? "语义茎轨道" : "频段踩点分布", false)) {
			if (!hasStemSeparation) {
				AudioAnalysisPanelImGui.detailRowCompact(state, "低频（鼓点）", asset.getLowCount() + " 个", AudioAnalysisPanelImGui.COLOR_LOW);
				AudioAnalysisPanelImGui.detailRowCompact(state, "中频（旋律）", asset.getMidCount() + " 个", AudioAnalysisPanelImGui.COLOR_MID);
				AudioAnalysisPanelImGui.detailRowCompact(state, "高频（打击）", asset.getHighCount() + " 个", AudioAnalysisPanelImGui.COLOR_HIGH);
				AudioAnalysisPanelImGui.compactGap();
				AudioAnalysisPanelImGui.renderBandBar(asset);
			} else {
				AudioAnalysisPanelImGui.detailRowCompact(state, "鼓组（drums）", AudioAnalysisPanelImGui.stemStateLabel(detailBm, "drums"), new ImVec4(0.87f, 0.53f, 0.25f, 1f));
				AudioAnalysisPanelImGui.detailRowCompact(state, "贝斯（bass）", AudioAnalysisPanelImGui.stemStateLabel(detailBm, "bass"), new ImVec4(0.27f, 0.60f, 0.87f, 1f));
				AudioAnalysisPanelImGui.detailRowCompact(state, "人声（vocals）", AudioAnalysisPanelImGui.stemStateLabel(detailBm, "vocals"), new ImVec4(0.67f, 0.38f, 0.84f, 1f));
				AudioAnalysisPanelImGui.detailRowCompact(state, "其他（other）", AudioAnalysisPanelImGui.stemStateLabel(detailBm, "other"), new ImVec4(0.58f, 0.72f, 0.30f, 1f));
				AudioAnalysisPanelImGui.compactGap();
				AudioAnalysisPanelImGui.textDisabledWrapped("提示：静音/独奏请在时间线音频子轨上操作。鼓类特征轨(kick/snare/hihat)会共同影响 drums 茎。");
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}

		if (AudioAnalysisPanelImGui.beginDetailSection("completed_demucs", "Demucs 拆分结果", false)) {
			if (hasStemSeparation) {
				AudioAnalysisPanelImGui.detailRowCompact(state, "状态", "已生成", new ImVec4(0.36f, 0.79f, 0.65f, 1f));
				String separationMode = detailBm.meta.separationMode();
				AudioAnalysisPanelImGui.detailRowCompact(state, "分离模式", separationMode != null && !separationMode.isBlank() ? separationMode : "demucs",
					new ImVec4(0.22f, 0.78f, 0.82f, 1f));
				AudioAnalysisPanelImGui.renderStemDetailRow(state, host, detailBm, "drums", "鼓组（drums）", new ImVec4(0.87f, 0.53f, 0.25f, 1f));
				AudioAnalysisPanelImGui.renderStemDetailRow(state, host, detailBm, "bass", "贝斯（bass）", new ImVec4(0.27f, 0.60f, 0.87f, 1f));
				AudioAnalysisPanelImGui.renderStemDetailRow(state, host, detailBm, "vocals", "人声（vocals）", new ImVec4(0.67f, 0.38f, 0.84f, 1f));
				AudioAnalysisPanelImGui.renderStemDetailRow(state, host, detailBm, "other", "其他（other）", new ImVec4(0.58f, 0.72f, 0.30f, 1f));
			} else {
				AudioAnalysisPanelImGui.detailRowCompact(state, "状态", "未生成（当前为基础分析 Basic）", new ImVec4(0.94f, 0.62f, 0.16f, 1f));
				AudioAnalysisPanelImGui.compactGap();
				AudioAnalysisPanelImGui.textDisabledWrapped("当前结果来自基础分析缓存或 Demucs 回退模式，因此没有 drums/bass/vocals/other 的拆分文件。若需查看拆分结果，请确保 Demucs 依赖可用后重新解析。");
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}

		if (AudioAnalysisPanelImGui.beginDetailSection("completed_actions", "操作", true)) {
			float actionW = (ImGui.getContentRegionAvailX() - 6f) * 0.5f;
			if (ImGui.button("清缓存并用 Demucs 重解析##detailReanalyzeDemucs", actionW, 26f)) {
				String result = AudioAssetManager.getInstance().clearCacheAndReanalyze(asset, AudioAnalysisMode.DEMUCS);
				state.setPanelHint(result, result.contains("未初始化") || result.contains("无效"));
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("删除该音频对应的 beatmap 和 stem 缓存后，以 Demucs 模式重新分析");
			}
			ImGui.sameLine();
			if (ImGui.button("清缓存并用 Basic 重解析##detailReanalyzeBasic", actionW, 26f)) {
				String result = AudioAssetManager.getInstance().clearCacheAndReanalyze(asset, AudioAnalysisMode.BASIC);
				state.setPanelHint(result, result.contains("未初始化") || result.contains("无效"));
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("删除该音频对应的 beatmap 和 stem 缓存后，以 Basic 模式重新分析");
			}

			AudioAnalysisPanelImGui.compactGap();
			AudioAnalysisMode compareMode = asset.getResolvedAnalysisMode() == AudioAnalysisMode.DEMUCS
				? AudioAnalysisMode.BASIC
				: AudioAnalysisMode.DEMUCS;
			String compareLabel = compareMode == AudioAnalysisMode.DEMUCS
				? "用 Demucs 做对比##detailCompareDemucs"
				: "用 Basic 做对比##detailCompareBasic";
			if (ImGui.button(compareLabel, ImGui.getContentRegionAvailX(), 24f)) {
				String result = AudioAssetManager.getInstance().clearCacheAndReanalyze(asset, compareMode);
				state.setPanelHint(result, result.contains("未初始化") || result.contains("无效"));
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(compareMode == AudioAnalysisMode.DEMUCS
					? "清缓存后用 Demucs 重跑，方便和当前结果对比"
					: "清缓存后用 Basic 重跑，方便和当前结果对比");
			}

			AudioAnalysisPanelImGui.compactGap();
			float btnW = ImGui.getContentRegionAvailX();
			ImGui.pushStyleColor(ImGuiCol.Button, 0.28f, 0.26f, 0.45f, 1f);
			ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.35f, 0.33f, 0.55f, 1f);
			ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.45f, 0.43f, 0.65f, 1f);
			ImGui.button(Icons.MENU + "  拖动到时间线##dragBtn", btnW, 28f);
			ImGui.popStyleColor(3);

			if (ImGui.beginDragDropSource(imgui.flag.ImGuiDragDropFlags.SourceAllowNullID)) {
				AudioAssetManager.getInstance().setCurrentDragAsset(asset);
				ImGui.setDragDropPayload(
					"BB_AUDIO_ASSET_ID",
					asset.getId().getBytes(),
					ImGuiCond.Once
				);
				ImGui.text(Icons.MUSIC_NOTE + " " + asset.getFileName());
				ImGui.textDisabled(String.format("%.1f BPM · %d 踩点",
					asset.getBpm(), asset.getBeatCount()));
				ImGui.endDragDropSource();
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}
	}

	private static void renderDetailAnalyzing(AudioAnalysisPanelUiState state, AudioAsset asset) {
		float progress = AudioAnalysisPanelImGui.computeProgress(asset);
		String statusText = asset.getProcessingStatusText();

		if (AudioAnalysisPanelImGui.beginDetailSection("analyzing_progress", "解析进度", false)) {
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
				ImGui.textWrapped("正在处理：" + statusText);
				ImGui.popStyleColor();
				ImGui.textDisabled("当前阶段：" + AudioAnalysisPanelImGui.analysisPhaseLabel(asset));
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

		if (AudioAnalysisPanelImGui.beginDetailSection("analyzing_steps", "步骤明细", false)) {
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
		if (AudioAnalysisPanelImGui.beginDetailSection("pending_basic", "基本信息", false)) {
			AudioAnalysisPanelImGui.detailRowCompact(state, "文件名", asset.getFileName());
			AudioAnalysisPanelImGui.detailRowCompact(state, "时长", String.format("%.1f 秒", asset.getDurationSeconds()));
			AudioAnalysisPanelImGui.compactGap();
			ImGui.textDisabled("尚未解析，点击“开始解析”即可提交任务。");
			AudioAnalysisPanelImGui.endDetailSection();
		}
		if (AudioAnalysisPanelImGui.beginDetailSection("pending_actions", "操作", true)) {
			if (ImGui.button("开始解析##detailAnalyze", ImGui.getContentRegionAvailX(), 26f)) {
				AudioAssetManager.getInstance().startAnalysis(asset);
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}
	}

	private static void renderDetailFailed(AudioAnalysisPanelUiState state, AudioAsset asset) {
		if (AudioAnalysisPanelImGui.beginDetailSection("failed_error", "错误信息", false)) {
			ImGui.pushStyleColor(ImGuiCol.Text, 0.87f, 0.30f, 0.30f, 1f);
			ImGui.textWrapped(asset.getErrorMessage() != null
				? asset.getErrorMessage()
				: "未知错误");
			ImGui.popStyleColor();
			if (asset.getInfoMessage() != null && !asset.getInfoMessage().isBlank()) {
				AudioAnalysisPanelImGui.compactGap();
				AudioAnalysisPanelImGui.textDisabledWrapped(asset.getInfoMessage());
			}
			AudioAnalysisPanelImGui.compactGap();
			ImGui.textDisabled("支持格式：MP3 · WAV · OGG · FLAC");
			AudioAnalysisPanelImGui.endDetailSection();
		}
		if (AudioAnalysisPanelImGui.beginDetailSection("failed_actions", "操作", true)) {
			if (ImGui.button("一键转换为 MP3##detailConvert", ImGui.getContentRegionAvailX(), 26f)) {
				boolean accepted = AudioAssetManager.getInstance().requestConvertToMp3(asset);
				if (!accepted) {
					asset.setErrorMessage("已记录转换请求。当前版本暂未接入自动转换器，请先手动转为 MP3/WAV 后重试。");
				}
			}
			AudioAnalysisPanelImGui.compactGap();
			if (ImGui.button("重试##detailRetry", ImGui.getContentRegionAvailX(), 26f)) {
				AudioAssetManager.getInstance().startAnalysis(asset, asset.getRequestedAnalysisMode());
			}
			AudioAnalysisPanelImGui.endDetailSection();
		}
	}
}
