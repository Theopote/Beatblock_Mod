package com.beatblock.ui.panels;

import com.beatblock.ui.i18n.BBTexts;
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
			ImGui.text(BBTexts.get("beatblock.rhythm_drop.title"));
			ImGui.separator();
			ImGui.textWrapped(BBTexts.get("beatblock.rhythm_drop.hint"));

			RhythmDropPanelPresenter.ViewState state = presenter.viewState();
			ImGui.textDisabled(BBTexts.get("beatblock.rhythm_drop.selection_info",
				state.selectionCount(), state.playheadSeconds()));
			ImGui.textDisabled(BBTexts.get("beatblock.rhythm_drop.beats", state.beatGridDescription()));

			ImGui.checkbox(BBTexts.get("beatblock.rhythm_drop.start_next_beat") + "##rhythmDropNextBeat", startAtNextBeat);

			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.rhythm_drop.fall_duration") + "##rhythmDropDuration", fallDurationBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.rhythm_drop.fall_height") + "##rhythmDropHeight", fallHeightBuffer);

			List<String> targetLabels = buildTargetLabels(state.stageObjects());
			if (targetIndex.get() >= targetLabels.size()) {
				targetIndex.set(0);
			}
			ImGui.setNextItemWidth(-1f);
			if (ImGui.beginCombo(BBTexts.get("beatblock.rhythm_drop.target") + "##rhythmDropTarget", targetLabels.get(targetIndex.get()))) {
				for (int i = 0; i < targetLabels.size(); i++) {
					if (ImGui.selectable(targetLabels.get(i), targetIndex.get() == i)) {
						targetIndex.set(i);
					}
				}
				ImGui.endCombo();
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.rhythm_drop.target.tooltip"));
			}

			boolean canGenerate = state.selectionCount() > 0;
			if (!canGenerate) ImGui.beginDisabled();
			if (ImGui.button(BBTexts.get("beatblock.rhythm_drop.generate") + "##rhythmDropGenerate", -1f, 0f)) {
				var result = presenter.generateFromSelection(buildGenerateRequest(state.stageObjects()));
				setStatusMessage(result.messageOrEmpty());
			}
			if (!canGenerate) ImGui.endDisabled();
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.rhythm_drop.generate.tooltip"));
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
		labels.add(BBTexts.get("beatblock.rhythm_drop.target.auto"));
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
