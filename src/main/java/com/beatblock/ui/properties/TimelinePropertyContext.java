package com.beatblock.ui.properties;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;

/**
 * 时间线属性适配器的渲染上下文：当前选中目标与时间线编辑状态。
 */
public record TimelinePropertyContext(
	EventPropertiesRef ref,
	Timeline timeline,
	TimelineEditor editor,
	SelectionState selection,
	EventPropertiesPresenter presenter
) {

	public static TimelinePropertyContext of(
		EventPropertiesRef ref,
		Timeline timeline,
		TimelineEditor editor,
		EventPropertiesPresenter presenter
	) {
		SelectionState selection = editor != null ? editor.getSelectionState() : null;
		return new TimelinePropertyContext(ref, timeline, editor, selection, presenter);
	}

	public int selectedAnimationEventCount() {
		if (timeline == null || selection == null || presenter == null) {
			return 0;
		}
		return presenter.countSelectedAnimationEvents(timeline, selection);
	}
}
