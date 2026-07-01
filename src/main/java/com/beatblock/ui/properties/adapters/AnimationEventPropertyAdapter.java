package com.beatblock.ui.properties.adapters;

import com.beatblock.ui.panels.EventPropertiesPanel;
import com.beatblock.ui.properties.IPropertyAdapter;
import com.beatblock.ui.properties.TimelinePropertyContext;
import com.beatblock.ui.properties.TimelinePropertyKinds;

/**
 * 方块动画事件属性适配器（含多选批量编辑）。
 */
public final class AnimationEventPropertyAdapter implements IPropertyAdapter<TimelinePropertyContext> {

	private EventPropertiesPanel editor;

	private EventPropertiesPanel editor() {
		if (editor == null) {
			editor = new EventPropertiesPanel();
		}
		return editor;
	}

	@Override
	public Class<TimelinePropertyContext> getTargetType() {
		return TimelinePropertyContext.class;
	}

	@Override
	public int getPriority() {
		return 100;
	}

	@Override
	public String getTitleKey() {
		return "beatblock.event.title";
	}

	@Override
	public boolean supports(Object target) {
		if (!(target instanceof TimelinePropertyContext ctx)) {
			return false;
		}
		return ctx.selectedAnimationEventCount() > 1 || TimelinePropertyKinds.isAnimationRef(ctx.ref());
	}

	@Override
	public boolean renderProperties(TimelinePropertyContext ctx) {
		editor().renderBody(ctx.ref(), ctx.timeline(), ctx.editor());
		return false;
	}
}
