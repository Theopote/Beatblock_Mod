package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.ui.presenter.TimelineTransportPresenter;
import imgui.ImGui;

/**
 * 时间线工具栏 Transport 图标条：播放控制、事件跳转、Marker、时间显示。
 */
final class TimelineToolbarTransportStrip {

	private static final String TOOLTIP_PLAY = "播放 (空格)";
	private static final String TOOLTIP_PAUSE = "暂停";
	private static final String TOOLTIP_STOP = "停止并回到起点";
	private static final String TOOLTIP_TO_START = "回到开头";
	private static final String TOOLTIP_TO_END = "跳到结尾";
	private static final String TOOLTIP_BACK_BEAT = "后退 1 拍（无 BPM 时后退 1 秒）；按住 Shift 后退 5 秒";
	private static final String TOOLTIP_FWD_BEAT = "前进 1 拍（无 BPM 时前进 1 秒）；按住 Shift 前进 5 秒";
	private static final String TOOLTIP_PREV_EVENT = "跳到上一事件点";
	private static final String TOOLTIP_NEXT_EVENT = "跳到下一事件点";
	private static final String TOOLTIP_ADD_MARKER = "在当前时间创建 Marker；也可双击标尺空白处";

	private final TimelineTransportPresenter transport;

	TimelineToolbarTransportStrip(TimelineTransportPresenter transport) {
		this.transport = transport;
	}

	void render(
		TimelineEditor editor,
		TimelineTransportPresenter.TransportViewState transportState,
		double stepSeek
	) {
		final float buttonSize = TimelineLayout.ROW_HEIGHT;
		String transportTooltip;
		IconButtonStyle.pushBeatBlockIconButton();

		if (ImGui.button(Icons.Play.REWIND_START + "##tlToStart", buttonSize, buttonSize)) {
			transport.seekTo(editor, 0);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(null, TOOLTIP_TO_START);
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.REWIND + "##tlBackBeat", buttonSize, buttonSize)) {
			transport.seekBy(editor, -stepSeek);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_BACK_BEAT);
		TimelineToolbarImGui.nextItemInGroup();

		if (transportState.playing()) {
			if (ImGui.button(Icons.Play.PAUSE + "##tlPause", buttonSize, buttonSize)) transport.pause();
			transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_PAUSE);
		} else {
			if (ImGui.button(Icons.Play.PLAY + "##tlPlay", buttonSize, buttonSize)) transport.play(editor);
			transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_PLAY);
		}
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.STOP + "##tlStop", buttonSize, buttonSize)) transport.stop(editor);
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_STOP);
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.FORWARD + "##tlFwdBeat", buttonSize, buttonSize)) {
			transport.seekBy(editor, stepSeek);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_FWD_BEAT);
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Play.FORWARD_END + "##tlToEnd", buttonSize, buttonSize)) {
			transport.seekTo(editor, transportState.durationSeconds());
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_TO_END);
		TimelineToolbarImGui.nextGroup();

		if (ImGui.button(Icons.Action.ARROW_LEFT + "##tlPrevEvt", buttonSize, buttonSize)) {
			transport.jumpToNearbyEvent(editor, false);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_PREV_EVENT);
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Action.ARROW_RIGHT + "##tlNextEvt", buttonSize, buttonSize)) {
			transport.jumpToNearbyEvent(editor, true);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_NEXT_EVENT);
		TimelineToolbarImGui.nextItemInGroup();

		if (ImGui.button(Icons.Timeline.MARKER + "##tlAddMarker", buttonSize, buttonSize)) {
			transport.addMarkerAtCurrentTime(editor);
		}
		transportTooltip = TimelineToolbarImGui.hoveredTooltip(transportTooltip, TOOLTIP_ADD_MARKER);
		IconButtonStyle.popBeatBlockIconButton();
		if (transportTooltip != null) ImGui.setTooltip(transportTooltip);

		TimelineToolbarImGui.nextGroup();
		ImGui.textDisabled(transportState.positionDisplay());
	}
}
