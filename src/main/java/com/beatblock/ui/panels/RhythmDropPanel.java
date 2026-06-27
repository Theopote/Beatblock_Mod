package com.beatblock.ui.panels;

import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.RhythmDropPanelPresenter;
import com.beatblock.ui.presenter.ToolPanelPresenter;
import com.beatblock.timeline.generation.RhythmDropEventFactory;
import com.beatblock.timeline.generation.RhythmDropGenerator;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 天降方块（RhythmDrop）生成面板：从当前方块选区按节拍写入 Timeline 动画事件。
 */
public class RhythmDropPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private final RhythmDropPanelPresenter presenter;
	private final ImBoolean startAtNextBeat = new ImBoolean(true);
	private final ImString fallDurationBuffer = new ImString(8);
	private final ImString fallHeightBuffer = new ImString(8);
	private final ImInt targetIndex = new ImInt(0);
	private String statusMessage;
	private long statusMessageTimeMs;

	public RhythmDropPanel() {
		this(PresenterFactories.rhythmDropPanelPresenter());
	}

	RhythmDropPanel(RhythmDropPanelPresenter presenter) {
		this.presenter = presenter;
		fallDurationBuffer.set(String.format(Locale.ROOT, "%.1f", RhythmDropEventFactory.DEFAULT_FALL_DURATION_SECONDS));
		fallHeightBuffer.set(String.format(Locale.ROOT, "%.1f", RhythmDropEventFactory.DEFAULT_FALL_HEIGHT_BLOCKS));
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.rhythmDropPanelWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.rhythmDropPanelWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			ImGui.text("天降方块（RhythmDrop）");
			ImGui.separator();
			ImGui.textWrapped(
				"选中已存在于世界中的落点方块，按节拍依次生成纯视觉下落动画（写入「方块动画」轨道）。"
					+ "每个事件精确落在对应坐标，命中瞬间触发粒子，不改变真实方块。");

			RhythmDropPanelPresenter.ViewState state = presenter.viewState();
			ImGui.textDisabled(String.format(Locale.ROOT,
				"当前选区：%d 个落点 · 起点 %.2fs（播放头）", state.selectionCount(), state.playheadSeconds()));
			ImGui.textDisabled("节拍：" + state.beatGridDescription());

			ImGui.checkbox("从下一拍开始（否则第一落在播放头）##rhythmDropNextBeat", startAtNextBeat);

			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("下落时长(秒)##rhythmDropDuration", fallDurationBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText("下落高度(格)##rhythmDropHeight", fallHeightBuffer);

			List<String> targetLabels = buildTargetLabels(state.stageObjects());
			if (targetIndex.get() >= targetLabels.size()) {
				targetIndex.set(0);
			}
			ImGui.setNextItemWidth(-1f);
			if (ImGui.beginCombo("目标 StageObject##rhythmDropTarget", targetLabels.get(targetIndex.get()))) {
				for (int i = 0; i < targetLabels.size(); i++) {
					if (ImGui.selectable(targetLabels.get(i), targetIndex.get() == i)) {
						targetIndex.set(i);
					}
				}
				ImGui.endCombo();
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("自动锚点仅用于时间线引用；实际落点由每个事件的 singleBlock 参数决定。");
			}

			boolean canGenerate = state.selectionCount() > 0;
			if (!canGenerate) ImGui.beginDisabled();
			if (ImGui.button("生成天降方块事件##rhythmDropGenerate", -1f, 0f)) {
				var result = presenter.generateFromSelection(buildGenerateRequest(state.stageObjects()));
				setStatusMessage(result.messageOrEmpty());
			}
			if (!canGenerate) ImGui.endDisabled();
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip("需先导入音乐以使用特征轨节拍；无节拍时按 Timeline BPM 固定间隔排布。");
			}

			if (statusMessage != null && !statusMessage.isBlank()
				&& System.currentTimeMillis() - statusMessageTimeMs < 5000L) {
				ImGui.textWrapped(statusMessage);
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.rhythmDropPanelWindow());
		}
	}

	private List<String> buildTargetLabels(List<ToolPanelPresenter.StageObjectListItem> objects) {
		List<String> labels = new ArrayList<>();
		labels.add("自动（rhythm_drop_anchor）");
		if (objects != null) {
			for (var obj : objects) {
				if (obj == null || RhythmDropGenerator.DEFAULT_ANCHOR_ID.equals(obj.id())) continue;
				labels.add(obj.name() + "  [" + obj.id() + "]");
			}
		}
		return labels;
	}

	private RhythmDropPanelPresenter.GenerateRequest buildGenerateRequest(
		List<ToolPanelPresenter.StageObjectListItem> objects
	) {
		String targetId = RhythmDropGenerator.DEFAULT_ANCHOR_ID;
		int idx = targetIndex.get();
		if (idx > 0 && objects != null) {
			int objectIndex = 0;
			for (var obj : objects) {
				if (obj == null || RhythmDropGenerator.DEFAULT_ANCHOR_ID.equals(obj.id())) continue;
				objectIndex++;
				if (objectIndex == idx) {
					targetId = obj.id();
					break;
				}
			}
		}
		return new RhythmDropPanelPresenter.GenerateRequest(
			ToolPanelPresenter.parseStaggerSeconds(fallDurationBuffer.get()),
			ToolPanelPresenter.parseStaggerSeconds(fallHeightBuffer.get()),
			startAtNextBeat.get(),
			targetId
		);
	}

	private void setStatusMessage(String msg) {
		if (msg == null || msg.isBlank()) return;
		statusMessage = msg;
		statusMessageTimeMs = System.currentTimeMillis();
	}
}
