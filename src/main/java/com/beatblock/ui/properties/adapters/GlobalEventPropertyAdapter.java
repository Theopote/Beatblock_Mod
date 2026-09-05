package com.beatblock.ui.properties.adapters;

import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.properties.IPropertyAdapter;
import com.beatblock.ui.properties.TimelinePropertyContext;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import com.beatblock.ui.properties.editors.GlobalPropertyEditor;

public final class GlobalEventPropertyAdapter implements IPropertyAdapter<TimelinePropertyContext> {

	private GlobalPropertyEditor editor;
	private EventPropertiesPresenter boundPresenter;

	private GlobalPropertyEditor editor(EventPropertiesPresenter presenter) {
		if (editor == null || boundPresenter != presenter) {
			editor = presenter != null
				? new GlobalPropertyEditor(presenter)
				: new GlobalPropertyEditor();
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
		return 80;
	}

	@Override
	public String getTitleKey() {
		return "beatblock.global.title";
	}

	@Override
	public boolean supports(Object target) {
		return target instanceof TimelinePropertyContext ctx && TimelinePropertyKinds.isGlobalRef(ctx.ref());
	}

	@Override
	public boolean renderProperties(TimelinePropertyContext ctx) {
		editor(ctx.presenter()).renderBody(ctx.ref(), ctx.timeline(), ctx.editor());
		return false;
	}
}
