package com.beatblock.ui.properties.adapters;

import com.beatblock.BeatBlock;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.properties.IPropertyAdapter;
import com.beatblock.ui.properties.TimelinePropertyContext;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import com.beatblock.ui.properties.editors.AnimationPropertyEditor;

/**
 * 方块动画事件属性适配器（含多选批量编辑）。
 */
public final class AnimationEventPropertyAdapter implements IPropertyAdapter<TimelinePropertyContext> {

	private AnimationPropertyEditor editor;
	private EventPropertiesPresenter boundPresenter;

	private AnimationPropertyEditor editor(EventPropertiesPresenter presenter) {
		if (editor == null || boundPresenter != presenter) {
			editor = presenter != null
				? new AnimationPropertyEditor(presenter, BeatBlock::getContext)
				: new AnimationPropertyEditor();
			boundPresenter = presenter;
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
		editor(ctx.presenter()).renderBody(ctx.ref(), ctx.timeline(), ctx.editor());
		return false;
	}
}
