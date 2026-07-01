package com.beatblock.ui.properties.adapters;

import com.beatblock.ui.properties.IPropertyAdapter;
import com.beatblock.ui.properties.TimelinePropertyContext;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import com.beatblock.ui.properties.editors.GlobalPropertyEditor;

public final class GlobalEventPropertyAdapter implements IPropertyAdapter<TimelinePropertyContext> {

	private GlobalPropertyEditor editor;

	private GlobalPropertyEditor editor() {
		if (editor == null) {
			editor = new GlobalPropertyEditor();
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
		editor().renderBody(ctx.ref(), ctx.timeline(), ctx.editor());
		return false;
	}
}
