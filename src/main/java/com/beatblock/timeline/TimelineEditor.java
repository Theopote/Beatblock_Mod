package com.beatblock.timeline;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.editor.*;
import com.beatblock.timeline.interaction.TimelineInteraction;
import com.beatblock.timeline.rendering.TimelineRenderer;
import imgui.ImGui;

/**
 * ImGui 时间线编辑器入口：协调渲染与交互，UI 与数据分离。
 * 职责：UI 入口、协调各子系统。
 */
public final class TimelineEditor {

	private static final float TRACK_LABEL_WIDTH = 110f;
	private static final float RULER_HEIGHT = 20f;

	private final Timeline timeline;
	private final com.beatblock.timeline.editor.TimelineEditor state;
	private final TimelineRenderer renderer;
	private final TimelineInteraction interactionSystem;
	private final CommandManager commandManager;

	public TimelineEditor(Timeline timeline) {
		this.timeline = timeline;
		this.state = new com.beatblock.timeline.editor.TimelineEditor(timeline);
		this.renderer = new TimelineRenderer();
		this.interactionSystem = new TimelineInteraction();
		this.commandManager = new CommandManager();
	}

	public Timeline getTimeline() {
		return timeline;
	}

	public com.beatblock.timeline.editor.TimelineEditor getState() {
		return state;
	}

	public TimelineClock getClock() {
		return state.getClock();
	}

	public TimelineViewState getViewState() {
		return state.getViewState();
	}

	public SelectionState getSelectionState() {
		return state.getSelectionState();
	}

	public InteractionState getInteractionState() {
		return state.getInteractionState();
	}

	public SelectionBox getSelectionBox() {
		return state.getSelectionBox();
	}

	public CommandManager getCommandManager() {
		return commandManager;
	}

	/** 同步时钟时长与 Timeline 一致 */
	public void syncClockDuration() {
		state.syncClockDuration();
	}

	/**
	 * 每帧调用：先同步时钟与视图，再绘制，再处理输入。
	 */
	public void render() {
		if (timeline == null) return;

		state.syncClockDuration();
		if (BeatBlock.musicPlayer != null && BeatBlock.musicPlayer.isPlaying()) {
			state.getClock().setCurrentTimeSeconds(BeatBlock.musicPlayer.getCurrentTimeSeconds());
		}

		float contentWidth = ImGui.getContentRegionAvailX();
		float timelineWidth = Math.max(200f, contentWidth - TRACK_LABEL_WIDTH - 20f);
		double duration = timeline.getDurationSeconds() > 0 ? timeline.getDurationSeconds() : 60.0;
		TimelineViewState viewState = state.getViewState();

		// 首次或默认可见范围时 fit
		if (viewState.getViewEndTimeSeconds() >= 59 && viewState.getViewEndTimeSeconds() <= 61 && duration > 0 && timelineWidth > 0) {
			viewState.fitToDuration(duration, timelineWidth);
		}

		float startY = ImGui.getCursorPosY();
		renderer.render(
			timeline,
			viewState,
			state.getSelectionState(),
			state.getClock(),
			state.getSelectionBox(),
			timelineWidth
		);

		float contentLeft = ImGui.getWindowPosX() + ImGui.getScrollX() + TRACK_LABEL_WIDTH;
		float scrollY = ImGui.getScrollY();
		float winY = ImGui.getWindowPosY();
		float rulerScreenTop = winY + scrollY + startY;
		float rulerScreenBottom = rulerScreenTop + RULER_HEIGHT;
		float baseContentScreenY = rulerScreenTop + RULER_HEIGHT;

		interactionSystem.update(
			timeline,
			viewState,
			state.getInteractionState(),
			state.getSelectionState(),
			state.getClock(),
			state.getSelectionBox(),
			contentLeft,
			timelineWidth,
			rulerScreenTop,
			rulerScreenBottom,
			baseContentScreenY
		);
	}
}
