package com.beatblock.timeline;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editor.*;
import com.beatblock.timeline.editing.TimelineEditSession;
import com.beatblock.timeline.interaction.TimelineInteraction;
import com.beatblock.timeline.playback.PlaybackSession;
import com.beatblock.timeline.rendering.TimelineAudioFeatureFillSupport;
import com.beatblock.timeline.rendering.TimelineFrameTrackSnapshot;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineRenderer;
import com.beatblock.timeline.rendering.TimelineAudioDropHandler;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.timeline.view.TimelineViewController;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * ImGui 时间线编辑器入口：协调渲染与交互，UI 与数据分离。
 * 职责：UI 入口、协调各子系统。
 */
public final class TimelineEditor {

	private final Timeline timeline;
	private final TimelineEditorState state;
	private final TimelineRenderer renderer;
	private final TimelineInteraction interactionSystem;
	private final TimelineEditSession editSession;
	private final @Nullable MusicPlayer musicPlayer;
	private final TimelineViewController viewController;
	private final @Nullable IAudioPlayer audioPlayer;
	private final PlaybackSession playbackSession;

	public float getCachedDividerScreenX() {
		return viewController.dividerScreenX();
	}

	public float getCachedDividerTopScreenY() {
		return viewController.dividerTopScreenY();
	}

	public float getCachedDividerContentBottomScreenY() {
		return viewController.dividerContentBottomScreenY();
	}

	public TimelineEditor(@NonNull Timeline timeline, @Nullable IAudioPlayer audioPlayer) {
		this.timeline = timeline;
		this.audioPlayer = audioPlayer;
		this.musicPlayer = audioPlayer instanceof MusicPlayer mp ? mp : null;
		this.state = new TimelineEditorState(timeline);
		this.viewController = new TimelineViewController(timeline, state.getViewState());
		this.renderer = new TimelineRenderer();
		this.interactionSystem = new TimelineInteraction();
		this.interactionSystem.setAudioPlayer(audioPlayer);
		this.interactionSystem.bindTimelineEditor(this);
		if (audioPlayer instanceof MusicPlayer musicPlayer) {
			this.interactionSystem.setMusicPlayer(musicPlayer);
		}
		CommandManager commandManager = new CommandManager();
		this.editSession = new TimelineEditSession(
			 timeline,
			 state.getSelectionState(),
			viewController.trackListState(),
			interactionSystem,
			commandManager,
			() -> state.getClock().getCurrentTimeSeconds()
		);
		this.interactionSystem.bindEditSession(editSession);
		this.playbackSession = new PlaybackSession(
			state.getClock(),
			timeline,
			viewController.toolbarState(),
			this.musicPlayer,
			audioPlayer
		);
	}

	/** 无音频源时使用（可独立运行和测试）。 */
	public TimelineEditor(@NonNull Timeline timeline) {
		this(timeline, null);
	}

	public @NonNull Timeline getTimeline() {
		return timeline;
	}

	public @NonNull TimelineEditorState getEditorState() {
		return state;
	}

	public @NonNull TimelineClock getClock() {
		return state.getClock();
	}

	/**
	 * 统一播放会话：时间读/写、播放/暂停、与音频同步请优先走此门面，
	 * 避免直接分别操作 Clock 与 MusicPlayer。
	 */
	public @NonNull PlaybackSession getPlaybackSession() {
		return playbackSession;
	}

	public @NonNull TimelineViewState getViewState() {
		return viewController.viewState();
	}

	public @NonNull TimelineViewController getViewController() {
		return viewController;
	}

	public @NonNull SelectionState getSelectionState() {
		return state.getSelectionState();
	}

	public @NonNull InteractionState getInteractionState() {
		return state.getInteractionState();
	}

	public @NonNull SelectionBox getSelectionBox() {
		return state.getSelectionBox();
	}

	public @NonNull CommandManager getCommandManager() {
		return editSession.commands();
	}

	public @NonNull TimelineEditSession getEditSession() {
		return editSession;
	}

	/** 打开/切换工程后丢弃 Undo/Redo 栈，避免旧命令引用已替换的时间线状态。 */
	public void clearUndoHistory() {
		editSession.clearHistory();
	}

	/**
	 * Abort live drag/resize preview mutations (Esc, lost capture, project switch, shutdown).
	 * Does not create an Undo entry.
	 */
	public void cancelLiveDocumentPreview() {
		interactionSystem.cancelLiveDocumentPreview(timeline, state.getInteractionState());
	}

	/**
	 * 将音频资产接入时间线（播放绑定、音频轨片段、分析回填），与拖入时间线行为一致。
	 */
	public void connectAudioAsset(@NonNull AudioAsset asset) {
		TimelineAudioDropHandler.handleDroppedAudioAsset(renderer, timeline, asset, -1);
	}

	/**
	 * 若该资产是当前时间线正在使用的音频，则标记等待新分析结果自动回填
	 * （重解析 / 切换分析模式后由 {@code TimelineDenseFeatureApplier} 应用，并走 protected merge）。
	 *
	 * @return true 表示已标记，完成后会自动写回 Timeline
	 */
	public boolean markAwaitingAnalyzedBeatmapIfActive(@NonNull AudioAsset asset) {
		if (!isActiveTimelineAudio(asset)) {
			return false;
		}
		String audioKey = TimelineAudioFeatureFillSupport.buildAudioAssetKey(asset);
		if (audioKey == null) {
			audioKey = TimelineAudioFeatureFillSupport.getTimelineAudioPathKey(timeline);
		}
		if (audioKey == null) {
			return false;
		}
		timeline.setMetadata("awaitingAnalyzedBeatmap", audioKey);
		renderer.resetBeatmapAutoApplySignature();
		return true;
	}

	/** 判断资产是否为当前时间线绑定的主音频（路径 key 或 audioAssetId）。 */
	public boolean isActiveTimelineAudio(@NonNull AudioAsset asset) {
		String assetKey = TimelineAudioFeatureFillSupport.buildAudioAssetKey(asset);
		String timelineKey = TimelineAudioFeatureFillSupport.getTimelineAudioPathKey(timeline);
		if (assetKey != null && java.util.Objects.equals(assetKey, timelineKey)) {
			return true;
		}
		Object assetIdMeta = timeline.getMetadata("audioAssetId");
		return assetIdMeta != null
			&& asset.getId() != null
			&& asset.getId().equals(String.valueOf(assetIdMeta));
	}

	public @NonNull TimelineToolbarState getToolbarState() {
		return viewController.toolbarState();
	}

	public @Nullable IAudioPlayer getAudioPlayer() {
		return audioPlayer;
	}

	public @NonNull TimelineTrackListState getTrackListState() {
		return viewController.trackListState();
	}

	public void beginFrameLayout() {
		viewController.beginFrame(renderer);
	}

	/** 本帧轨模型（只读）；供测试与调试。 */
	public @NonNull TimelineFrameTrackSnapshot getFrameTrackSnapshot() {
		return viewController.frameTrackSnapshot();
	}

	private TimelineLayout requireFrameLayout() {
		return viewController.frameLayout(renderer);
	}

	private TimelineLayout requireTrackAreaLayout() {
		return viewController.trackAreaLayout(renderer);
	}

	/**
	 * 在父窗口标尺带点击分割线开始拖动（子窗口未覆盖标尺区域，需在此处命中）。
	 */
	public void tryBeginTimelineDividerDragOnRuler() {
		if (timeline == null) return;
		TimelineLayout l = requireFrameLayout();
		interactionSystem.tryBeginDividerDragOnRuler(getTrackListState(), getInteractionState(), l);
	}

	/** 在主窗口标尺上下文处理交互（Scrub / Loop Handle / Marker / 右键等）。 */
	public void handleRulerInteraction() {
		if (timeline == null) return;
		TimelineLayout layout = requireFrameLayout();
		interactionSystem.updateRulerOnly(
			timeline,
			state.getViewState(),
			state.getInteractionState(),
			state.getSelectionState(),
			state.getClock(),
			layout,
			getToolbarState()
		);
	}

	/** 同步时钟时长与 Timeline 一致 */
	public void syncClockDuration() {
		state.syncClockDuration();
	}

	/**
	 * 固定区域：只绘制时间刻度（标尺）行，并占位，不随滚动条滚动。在 TimelinePanel 中先调用此方法，再 BeginChild。
	 *
	 * @param activePlaybackPlayer 当前实际驱动播放的音频源（分轨时为 {@link com.beatblock.audio.StemMixer}）；
	 *                             为 null 时回退到构造时注入的 audioPlayer
	 */
	public void renderRulerOnly(@Nullable IAudioPlayer activePlaybackPlayer) {
		if (timeline == null) return;
		state.syncClockDuration();
		playbackSession.syncFromAudio(activePlaybackPlayer);
		TimelineLayout layout = requireFrameLayout();
		viewController.updateRulerDivider(layout);
		double duration = timeline.getDurationSeconds() > 0 ? timeline.getDurationSeconds() : 60.0;
		TimelineViewState viewState = state.getViewState();
		if (viewState.getViewEndTimeSeconds() >= 59 && viewState.getViewEndTimeSeconds() <= 61 && duration > 0 && layout.contentWidth > 0) {
			viewState.fitToDuration(duration, layout.contentWidth);
		}
		renderer.renderRulerRow(layout, viewState, timeline.getBpm(), getToolbarState(), timeline);
	}

	/**
	 * 可滚动区域：在 BeginChild 内调用，绘制轨道区（网格 + 一行一行轨道 + 播放头 + 框选），并处理交互。
	 */
	public void renderTrackArea() {
		if (timeline == null) return;
		TimelineLayout layout = requireTrackAreaLayout();
		viewController.updateTrackAreaDivider(layout);
		TimelineViewState viewState = state.getViewState();
		interactionSystem.update(
			timeline,
			viewState,
			state.getInteractionState(),
			state.getSelectionState(),
			state.getClock(),
			state.getSelectionBox(),
			getTrackListState(),
			layout,
			getToolbarState()
		);
		renderer.renderTrackArea(
			timeline,
			viewState,
			state.getSelectionState(),
			state.getClock(),
			state.getSelectionBox(),
			state.getInteractionState(),
			getTrackListState(),
			layout,
			getToolbarState(),
			getFrameTrackSnapshot()
		);
		viewController.finishTrackAreaFrame();
	}

	/**
	 * 在 TimelinePanel 中于轨道子窗口绘制完成后调用，绘制标尺区播放头竖线（轨道区段在子窗口内单独绘制）。
	 */
	public void renderPlayheadOverlay() {
		if (timeline == null) return;
		TimelineLayout layout = requireFrameLayout();
		TimelineViewState viewState = state.getViewState();
		TimelineClock clock = state.getClock();
		if (viewState == null || clock == null) return;

		TimelineRenderer.drawPlayheadLine(
			layout,
			viewState,
			clock.getCurrentTimeSeconds(),
			layout.rulerTop,
			layout.rulerTop + layout.rulerHeight
		);
	}

	/**
	 * 编辑器生命周期结束时释放后台资源。
	 */
	public void shutdown() {
		cancelLiveDocumentPreview();
		renderer.shutdown();
	}

	public void copySelectedEvents() {
		editSession.copy();
	}

	public void pasteClipboardAtPlayhead() {
		editSession.pasteAtPlayhead();
	}

	public void pasteClipboardAt(double anchorTimeSeconds) {
		editSession.pasteAt(anchorTimeSeconds);
	}

	public void cutSelectedEvents() {
		editSession.cut();
	}

	public void deleteSelectedEntries() {
		editSession.deleteSelection();
	}

	public boolean duplicateSelectedEntries() {
		return editSession.duplicateSelection();
	}

	public boolean splitSelectedClipAtPlayhead() {
		return editSession.splitAtPlayhead();
	}

	public boolean hasDeletableSelection() {
		return editSession.canDelete();
	}

	public boolean hasClipboardContent() {
		return editSession.hasClipboardContent();
	}

	public boolean hasTimelineSelection() {
		return editSession.hasSelection();
	}
}
