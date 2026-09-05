package com.beatblock.ui.presenter;

import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.audio.IAudioAnalyzer;
import com.beatblock.audio.assets.AudioAnalysisMode;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.audio.assets.AudioAssetStatus;
import com.beatblock.audio.python.PythonEnvironmentDiagnostics;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * 音频解析面板：Demucs / Python 状态、资产生命周期，以及应用到时间线（经 Context / AssetManager 注入）。
 */
public final class AudioAnalysisPanelPresenter {

	private final Supplier<BeatBlockContext> context;
	private final Supplier<AudioAssetManager> assetManager;

	public AudioAnalysisPanelPresenter(Supplier<BeatBlockContext> context) {
		this(context, AudioAssetManager::getInstance);
	}

	public AudioAnalysisPanelPresenter(
		Supplier<BeatBlockContext> context,
		Supplier<AudioAssetManager> assetManager
	) {
		this.context = context;
		this.assetManager = assetManager != null ? assetManager : AudioAssetManager::getInstance;
	}

	public AudioAssetManager assets() {
		return assetManager.get();
	}

	public List<AudioAsset> listAssets() {
		return assets().getAssets();
	}

	public void startAnalysis(AudioAsset asset) {
		assets().startAnalysis(asset);
		markTimelineAwaitingIfActive(asset);
	}

	public void startAnalysis(AudioAsset asset, AudioAnalysisMode mode) {
		assets().startAnalysis(asset, mode);
		markTimelineAwaitingIfActive(asset);
	}

	public void removeAsset(String assetId) {
		assets().remove(assetId);
	}

	/**
	 * 清缓存并重解析；若该资产是当前 Timeline 主音频，则标记 awaiting，
	 * 分析完成后自动回填 beatmap / MusicStructure（含 protected section merge）。
	 */
	public String clearCacheAndReanalyze(AudioAsset asset, AudioAnalysisMode mode) {
		String result = assets().clearCacheAndReanalyze(asset, mode);
		if (markTimelineAwaitingIfActive(asset)) {
			return result + " " + BBTexts.get("beatblock.audio.reanalyze_will_apply_timeline");
		}
		return result;
	}

	/**
	 * 打开时间线审阅结构：若资产尚未接入当前时间线则先应用，再导航到时间线。
	 */
	public PresenterResult reviewStructureInTimeline(AudioAsset asset) {
		if (asset == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.audio.apply_timeline.need_complete"));
		}
		TimelineEditor editor = context.get().timelineEditor();
		if (editor == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.timeline_unavailable"));
		}
		if (!editor.isActiveTimelineAudio(asset)) {
			PresenterResult apply = applyToTimeline(asset);
			if (!apply.ok()) {
				return apply;
			}
		}
		return PresenterResult.success(BBTexts.get("beatblock.audio.review_structure.ok"));
	}

	private boolean markTimelineAwaitingIfActive(@Nullable AudioAsset asset) {
		if (asset == null) {
			return false;
		}
		AudioAssetStatus status = asset.getStatus();
		boolean analysisStarted = status == AudioAssetStatus.QUEUED
			|| status == AudioAssetStatus.ANALYZING
			|| status == AudioAssetStatus.PENDING;
		if (!analysisStarted) {
			return false;
		}
		TimelineEditor editor = context.get().timelineEditor();
		return editor != null && editor.markAwaitingAnalyzedBeatmapIfActive(asset);
	}

	public boolean requestConvertToMp3(AudioAsset asset) {
		return assets().requestConvertToMp3(asset);
	}

	public void setCurrentDragAsset(@Nullable AudioAsset asset) {
		assets().setCurrentDragAsset(asset);
	}

	public int queuePosition(String assetId) {
		return assets().getQueuePosition(assetId);
	}

	public boolean canMoveQueueUp(String assetId) {
		return assets().canMoveQueueUp(assetId);
	}

	public boolean canMoveQueueDown(String assetId) {
		return assets().canMoveQueueDown(assetId);
	}

	public void moveQueueUp(String assetId) {
		assets().moveQueueUp(assetId);
	}

	public void moveQueueDown(String assetId) {
		assets().moveQueueDown(assetId);
	}

	public void moveQueueBefore(String movingAssetId, String targetAssetId) {
		assets().moveQueueBefore(movingAssetId, targetAssetId);
	}

	public record ImportOutcome(PresenterResult result, @Nullable AudioAsset asset) {
		public boolean ok() {
			return result.ok();
		}

		public String message() {
			return result.messageOrEmpty();
		}
	}

	public ImportOutcome importAndAnalyze(String path) {
		AudioAssetManager manager = assets();
		if (path == null || path.isBlank()) {
			return new ImportOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.audio.path_invalid")),
				null
			);
		}
		if (!manager.isSupportedAudioPath(path)) {
			return new ImportOutcome(
				PresenterResult.failure(BBTexts.get(
					"beatblock.audio.unsupported_extensions",
					manager.getSupportedAudioExtensionsLabel()
				)),
				null
			);
		}
		AudioAsset asset = manager.addFromPath(path);
		if (asset == null) {
			return new ImportOutcome(
				PresenterResult.failure(BBTexts.get("beatblock.audio.path_invalid")),
				null
			);
		}
		manager.startAnalysis(asset);
		return new ImportOutcome(
			PresenterResult.success(BBTexts.get("beatblock.audio.added_and_analyzing", asset.getFileName())),
			asset
		);
	}

	/**
	 * 将已完成分析的音频资产接入当前时间线（播放绑定、特征轨、编舞结构种子），与拖入时间线一致。
	 */
	public PresenterResult applyToTimeline(AudioAsset asset) {
		if (asset == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.audio.apply_timeline.need_complete"));
		}
		if (asset.getStatus() != AudioAssetStatus.COMPLETED || asset.getBeatmap() == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.audio.apply_timeline.need_complete"));
		}
		TimelineEditor editor = context.get().timelineEditor();
		if (editor == null) {
			return PresenterResult.failure(BBTexts.get("beatblock.message.timeline_unavailable"));
		}
		editor.connectAudioAsset(asset);
		int sectionCount = asset.getBeatmap().sections != null ? asset.getBeatmap().sections.size() : asset.getSectionCount();
		return PresenterResult.success(BBTexts.get(
			"beatblock.audio.apply_timeline.ok",
			asset.getFileName(),
			asset.getBpm(),
			sectionCount
		));
	}

	public AudioAnalysisService externalAnalyzer() {
		return context.get().externalAudioAnalyzer();
	}

	public boolean isAnalyzerAvailable() {
		return externalAnalyzer() != null;
	}

	public boolean isUseDemucs() {
		AudioAnalysisService analyzer = externalAnalyzer();
		return analyzer != null && analyzer.isUseDemucs();
	}

	public void setUseDemucs(boolean enabled) {
		AudioAnalysisService analyzer = externalAnalyzer();
		if (analyzer != null) {
			analyzer.setUseDemucs(enabled);
		}
	}

	public String pythonRuntimeSummary() {
		AudioAnalysisService analyzer = externalAnalyzer();
		if (analyzer == null) {
			return null;
		}
		return analyzer.getPythonRuntimeSummary();
	}

	public PythonEnvironmentDiagnostics.RuntimeHealthSnapshot runtimeHealthSnapshot() {
		AudioAnalysisService analyzer = externalAnalyzer();
		return analyzer != null ? analyzer.getRuntimeHealthSnapshot() : null;
	}

	public IAudioAnalyzer backendAnalyzer() {
		AudioAnalysisService analyzer = externalAnalyzer();
		return analyzer != null ? analyzer.getAnalyzer() : null;
	}

	public int activeAnalysisCount() {
		AudioAnalysisService analyzer = externalAnalyzer();
		return analyzer != null ? analyzer.getActiveAnalysisCount() : 0;
	}
}
