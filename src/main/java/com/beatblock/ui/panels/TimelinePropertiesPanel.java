package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.properties.IPropertyAdapter;
import com.beatblock.ui.properties.PropertyAdapterBootstrap;
import com.beatblock.ui.properties.PropertyAdapterRegistry;
import com.beatblock.ui.properties.TimelinePropertyContext;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.function.Supplier;

/**
 * 统一时间线属性面板：根据时间线选中项自动切换适配器（对齐 Director {@code PropertiesPanel}）。
 */
public class TimelinePropertiesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private final EventPropertiesPresenter presenter;
	private final Supplier<BeatBlockContext> context;

	public TimelinePropertiesPanel() {
		this(PresenterFactories.eventPropertiesPresenter(), BeatBlock::getContext);
	}

	TimelinePropertiesPanel(EventPropertiesPresenter presenter, Supplier<BeatBlockContext> context) {
		this.presenter = presenter;
		this.context = context;
		PropertyAdapterBootstrap.initialize();
	}

	private BeatBlockContext runtime() {
		return context.get();
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.timelinePropertiesWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.timelinePropertiesWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			Timeline timeline = runtime() != null ? runtime().timeline() : null;
			TimelineEditor editor = runtime() != null ? runtime().timelineEditor() : null;
			if (timeline == null || editor == null) {
				ImGui.textDisabled(BBTexts.get("beatblock.common.timeline_not_initialized"));
				return;
			}

			EventPropertiesRef ref = presenter.resolvePropertiesRef(timeline, editor.getSelectionState());
			TimelinePropertyContext propertyContext = TimelinePropertyContext.of(
				ref,
				timeline,
				editor,
				presenter
			);

			IPropertyAdapter<TimelinePropertyContext> adapter = PropertyAdapterRegistry.getAdapterFor(propertyContext);
			if (adapter == null) {
				ImGui.text(BBTexts.get("beatblock.panel.timeline_properties"));
				ImGui.separator();
				ImGui.textWrapped(BBTexts.get("beatblock.properties.select_hint"));
				return;
			}

			ImGui.text(BBTexts.get(adapter.getTitleKey()));
			ImGui.separator();
			adapter.renderProperties(propertyContext);
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.timelinePropertiesWindow());
		}
	}
}
