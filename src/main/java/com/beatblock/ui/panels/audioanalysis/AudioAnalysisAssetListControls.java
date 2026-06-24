package com.beatblock.ui.panels.audioanalysis;

import com.beatblock.audio.assets.AudioAnalysisStep;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.audio.assets.AudioAssetStatus;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.ui.icons.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

import java.util.List;

/** 资产列表、列表项与底栏。 */
final class AudioAnalysisAssetListControls {

	private AudioAnalysisAssetListControls() {
	}

	static void renderAssetList(AudioAnalysisPanelHost host, List<AudioAsset> assets) {
		if (assets.isEmpty()) {
			ImGui.spacing();
			AudioAnalysisPanelImGui.centerText("尚未添加音频文件");
			return;
		}

		for (AudioAsset asset : assets) {
			renderAssetItem(host, asset);
			ImGui.dummy(0f, 4f);
		}
	}

	static void renderAssetItem(AudioAnalysisPanelHost host, AudioAsset asset) {
		AudioAnalysisPanelUiState state = host.uiState();
		boolean isSelected = state.selectedAsset() != null
			&& state.selectedAsset().getId().equals(asset.getId());

		float itemH = estimateItemHeight(asset);

		if (isSelected) {
			ImGui.pushStyleColor(ImGuiCol.ChildBg,
				AudioAnalysisPanelImGui.COLOR_SELECTED_BG.x,
				AudioAnalysisPanelImGui.COLOR_SELECTED_BG.y,
				AudioAnalysisPanelImGui.COLOR_SELECTED_BG.z,
				AudioAnalysisPanelImGui.COLOR_SELECTED_BG.w);
		} else {
			ImGui.pushStyleColor(ImGuiCol.ChildBg,
				AudioAnalysisPanelImGui.COLOR_HOVER_BG.x,
				AudioAnalysisPanelImGui.COLOR_HOVER_BG.y,
				AudioAnalysisPanelImGui.COLOR_HOVER_BG.z, 0f);
		}

		ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 4f);
		ImGui.beginChild("##item_" + asset.getId(), 0f, itemH, true, ImGuiWindowFlags.NoScrollbar);
		ImGui.popStyleVar();
		ImGui.popStyleColor();

		if (ImGui.isWindowHovered() && ImGui.isMouseClicked(0)) {
			state.setSelectedAsset(asset);
		}

		renderItemHeader(asset);

		ImGui.dummy(0f, 2f);
		switch (asset.getStatus()) {
			case PENDING -> renderPendingContent(host, asset);
			case QUEUED -> renderQueuedContent(host, asset);
			case ANALYZING -> renderAnalyzingContent(asset);
			case COMPLETED -> renderCompletedContent(asset);
			case FAILED -> renderFailedContent(host, asset);
		}

		ImGui.endChild();
	}

	static float estimateItemHeight(AudioAsset asset) {
		float lineH = ImGui.getTextLineHeightWithSpacing();
		return switch (asset.getStatus()) {
			case PENDING -> lineH * 2f + 28f;
			case QUEUED -> lineH * 3f + 20f;
			case ANALYZING -> {
				float base = lineH * 3f + 48f;
				String statusText = asset.getProcessingStatusText();
				if (statusText != null && !statusText.isBlank()) {
					float infoExtra = (asset.getInfoMessage() != null && !asset.getInfoMessage().isBlank()) ? lineH * 2f : 0f;
					yield base + lineH * 3f + infoExtra;
				}
				yield base + lineH + AudioAnalysisStep.values().length * lineH;
			}
			case COMPLETED -> lineH * 4f + 18f;
			case FAILED -> lineH * 3f + 28f;
		};
	}

	static void renderItemHeader(AudioAsset asset) {
		float dotX = ImGui.getCursorScreenPosX() + ImGui.getContentRegionAvailX() - 12f;
		float dotY = ImGui.getCursorScreenPosY() + ImGui.getTextLineHeight() * 0.5f;
		int dotColor = AudioAnalysisPanelImGui.statusDotColor(asset.getStatus());
		ImGui.getWindowDrawList().addCircleFilled(dotX, dotY, 4f, dotColor);

		ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 20f);
		ImGui.text(asset.getFileName());

		ImGui.sameLine();
		AudioAnalysisPanelImGui.renderModeBadge(asset);

		ImGui.textDisabled(String.format("%.1fs · %dHz",
			asset.getDurationSeconds(), asset.getSampleRate()));
	}

	private static void renderPendingContent(AudioAnalysisPanelHost host, AudioAsset asset) {
		AudioAnalysisPanelUiState state = host.uiState();
		if (ImGui.button("解析##" + asset.getId())) {
			AudioAssetManager.getInstance().startAnalysis(asset);
		}
		ImGui.sameLine();
		if (ImGui.button("移除##" + asset.getId())) {
			AudioAssetManager.getInstance().remove(asset.getId());
			state.clearSelectedAssetIfMatches(asset);
		}
	}

	private static void renderAnalyzingContent(AudioAsset asset) {
		float progress = AudioAnalysisPanelImGui.computeProgress(asset);

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
		ImGui.progressBar(progress, -1f, 6f, "");
		ImGui.popStyleColor(2);

		ImGui.sameLine(0f, 6f);
		ImGui.textDisabled(String.format("%.0f%%", progress * 100f));

		String statusText = asset.getProcessingStatusText();
		if (statusText != null && !statusText.isBlank()) {
			ImGui.spacing();
			ImGui.pushStyleColor(ImGuiCol.Text,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.x,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.y,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.z,
				AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.w);
			ImGui.textWrapped("正在处理：" + statusText);
			ImGui.popStyleColor();
			ImGui.textDisabled("阶段：" + AudioAnalysisPanelImGui.analysisPhaseLabel(asset));
		}

		if (asset.getInfoMessage() != null && !asset.getInfoMessage().isBlank()) {
			ImGui.spacing();
			AudioAnalysisPanelImGui.textDisabledWrapped(asset.getInfoMessage());
		}

		if (statusText != null && !statusText.isBlank()) {
			return;
		}

		ImGui.spacing();
		for (AudioAnalysisStep step : AudioAnalysisStep.values()) {
			boolean done = asset.getFinishedSteps().contains(step);
			boolean active = AudioAnalysisPanelImGui.isActiveStep(asset, step);
			String stepLabel = AudioAnalysisPanelImGui.stepLabel(step);

			if (done) {
				ImGui.pushStyleColor(ImGuiCol.Text,
					AudioAnalysisPanelImGui.COLOR_MID.x,
					AudioAnalysisPanelImGui.COLOR_MID.y,
					AudioAnalysisPanelImGui.COLOR_MID.z,
					AudioAnalysisPanelImGui.COLOR_MID.w);
				ImGui.text(Icons.CHECK + " " + stepLabel);
				ImGui.popStyleColor();
			} else if (active) {
				ImGui.pushStyleColor(ImGuiCol.Text,
					AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.x,
					AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.y,
					AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.z,
					AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.w);
				ImGui.text("▷ " + stepLabel + "...");
				ImGui.popStyleColor();
			} else {
				ImGui.textDisabled("  " + stepLabel);
			}
		}
	}

	private static void renderQueuedContent(AudioAnalysisPanelHost host, AudioAsset asset) {
		AudioAnalysisPanelUiState state = host.uiState();
		AudioAssetManager manager = AudioAssetManager.getInstance();
		int pos = manager.getQueuePosition(asset.getId());
		ImGui.pushStyleColor(ImGuiCol.Text, 0.95f, 0.78f, 0.38f, 1f);
		if (pos > 0) {
			ImGui.text("排队中 #" + pos);
		} else {
			ImGui.text("排队中");
		}
		ImGui.popStyleColor();
		ImGui.sameLine();
		AudioAnalysisPanelImGui.renderQueueBadge(asset);

		if (ImGui.beginDragDropSource(imgui.flag.ImGuiDragDropFlags.SourceAllowNullID)) {
			ImGui.setDragDropPayload("BB_AUDIO_QUEUE_ID", asset.getId().getBytes(), ImGuiCond.Once);
			ImGui.text("调整队列顺序: " + asset.getFileName());
			ImGui.endDragDropSource();
		}

		if (ImGui.beginDragDropTarget()) {
			byte[] raw = ImGui.acceptDragDropPayload("BB_AUDIO_QUEUE_ID");
			if (raw != null) {
				String movingId = AudioAnalysisPanelImGui.decodePayloadText(raw);
				if (!movingId.isBlank()) {
					manager.moveQueueBefore(movingId, asset.getId());
				}
			}
			ImGui.endDragDropTarget();
		}

		ImGui.spacing();
		ImGui.textDisabled("当前任务将按顺序自动开始解析");
		ImGui.textDisabled("可拖动队列项到此处以调整优先级");

		ImGui.spacing();
		boolean canMoveUp = manager.canMoveQueueUp(asset.getId());
		boolean canMoveDown = manager.canMoveQueueDown(asset.getId());

		if (canMoveUp) {
			if (ImGui.button("上移##queue_up_" + asset.getId())) {
				manager.moveQueueUp(asset.getId());
			}
		} else {
			ImGui.textDisabled("上移");
		}
		ImGui.sameLine();
		if (canMoveDown) {
			if (ImGui.button("下移##queue_down_" + asset.getId())) {
				manager.moveQueueDown(asset.getId());
			}
		} else {
			ImGui.textDisabled("下移");
		}
		ImGui.sameLine();
		if (ImGui.button("移除##remove_queue_" + asset.getId())) {
			manager.remove(asset.getId());
			state.clearSelectedAssetIfMatches(asset);
		}
	}

	private static void renderCompletedContent(AudioAsset asset) {
		ImGui.pushStyleColor(ImGuiCol.Text,
			AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.x,
			AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.y,
			AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.z,
			AudioAnalysisPanelImGui.COLOR_PROGRESS_FG.w);
		ImGui.text(String.format("%.1f BPM", asset.getBpm()));
		ImGui.popStyleColor();
		ImGui.sameLine();
		ImGui.textDisabled("·");
		ImGui.sameLine();
		ImGui.pushStyleColor(ImGuiCol.Text,
			AudioAnalysisPanelImGui.COLOR_MID.x,
			AudioAnalysisPanelImGui.COLOR_MID.y,
			AudioAnalysisPanelImGui.COLOR_MID.z,
			AudioAnalysisPanelImGui.COLOR_MID.w);
		ImGui.text(asset.getBeatCount() + " 踩点");
		ImGui.popStyleColor();

		ImGui.sameLine();
		AudioAnalysisPanelImGui.renderCacheBadge(asset.getCacheSource());
		Beatmap bm = asset.getBeatmap();
		if (bm != null && bm.meta != null && bm.meta.hasStemSeparation()) {
			ImGui.sameLine();
			ImGui.textDisabled("·");
			ImGui.sameLine();
			ImGui.pushStyleColor(ImGuiCol.Text, 0.22f, 0.78f, 0.82f, 1f);
			int stemCount = bm.meta.stems() != null ? bm.meta.stems().size() : 4;
			ImGui.text(stemCount + "茎");
			ImGui.popStyleColor();
		}

		ImGui.spacing();
		ImGui.textDisabled(Icons.MENU + " 拖动到时间线音频轨道");

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
	}

	private static void renderFailedContent(AudioAnalysisPanelHost host, AudioAsset asset) {
		AudioAnalysisPanelUiState state = host.uiState();
		ImGui.pushStyleColor(ImGuiCol.Text, 0.87f, 0.30f, 0.30f, 1f);
		String msg = asset.getErrorMessage() != null
			? asset.getErrorMessage()
			: "解析失败，请检查文件格式";
		ImGui.textWrapped(msg);
		ImGui.popStyleColor();

		ImGui.spacing();
		if (ImGui.button("重试##retry_" + asset.getId())) {
			AudioAssetManager.getInstance().startAnalysis(asset, asset.getRequestedAnalysisMode());
		}
		ImGui.sameLine();
		if (ImGui.button("转换为MP3##convert_" + asset.getId())) {
			boolean accepted = AudioAssetManager.getInstance().requestConvertToMp3(asset);
			if (!accepted) {
				asset.setErrorMessage("已记录转换请求。当前版本暂未接入自动转换器，请先手动转为 MP3/WAV 后重试。");
			}
		}
		ImGui.sameLine();
		if (ImGui.button("移除##remove_failed_" + asset.getId())) {
			AudioAssetManager.getInstance().remove(asset.getId());
			state.clearSelectedAssetIfMatches(asset);
		}
	}

	static void renderFooter(AudioAnalysisPanelHost host, List<AudioAsset> assets) {
		AudioAnalysisPanelUiState state = host.uiState();
		float clearDoneWidth = ImGui.calcTextSize("清除已完成").x + 8f;
		state.prunePanelHint();

		AudioAsset runningAsset = null;
		int queuedCount = 0;
		for (AudioAsset a : assets) {
			if (a.getStatus() == AudioAssetStatus.ANALYZING && runningAsset == null) {
				runningAsset = a;
			}
			if (a.getStatus() == AudioAssetStatus.QUEUED) {
				queuedCount++;
			}
		}

		if (ImGui.button("清除已完成##clearDone", clearDoneWidth, AudioAnalysisPanelImGui.FOOTER_BUTTON_HEIGHT)) {
			assets.stream()
				.filter(a -> a.getStatus() == AudioAssetStatus.COMPLETED)
				.map(AudioAsset::getId)
				.toList()
				.forEach(id -> AudioAssetManager.getInstance().remove(id));
			state.clearSelectedAssetIfCompleted();
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip("从列表中移除所有已解析完成的项目（不删除 beatmap 文件）");

		if (state.hasPanelHint()) {
			ImGui.sameLine();
			if (state.panelHintError()) {
				ImGui.pushStyleColor(ImGuiCol.Text, 0.92f, 0.36f, 0.36f, 1f);
				ImGui.text(state.panelHintText());
				ImGui.popStyleColor();
			} else {
				ImGui.textDisabled(state.panelHintText());
			}
		}

		if (runningAsset != null || queuedCount > 0) {
			ImGui.sameLine();
			ImGui.pushStyleColor(ImGuiCol.Text, 0.65f, 0.74f, 0.92f, 1f);
			String running = runningAsset != null
				? ("执行中: " + runningAsset.getFileName())
				: "执行中: 无";
			String queueText = "队列: " + queuedCount;
			ImGui.text(running + " · " + queueText);
			ImGui.popStyleColor();
		}

		long doneCount = assets.stream()
			.filter(a -> a.getStatus() == AudioAssetStatus.COMPLETED).count();
		String countText = String.format("%d / %d 已完成", doneCount, assets.size());
		float textW = ImGui.calcTextSize(countText).x;
		ImGui.sameLine(ImGui.getContentRegionAvailX() - textW + ImGui.getCursorPosX());
		ImGui.textDisabled(countText);
	}
}
