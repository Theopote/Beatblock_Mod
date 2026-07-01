package com.beatblock.ui.properties.adapters;

import com.beatblock.ui.properties.IPropertyAdapter;
import com.beatblock.ui.properties.TimelinePropertyContext;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import com.beatblock.ui.properties.editors.BuildLayerClipPropertyEditor;

public final class BuildLayerClipPropertyAdapter implements IPropertyAdapter<TimelinePropertyContext> {

	private BuildLayerClipPropertyEditor editor;

	private BuildLayerClipPropertyEditor editor() {
		if (editor == null) {
			editor = new BuildLayerClipPropertyEditor();
		}
		return editor;
	}

	@Override
	public Class<TimelinePropertyContext> getTargetType() {
		return TimelinePropertyContext.class;
	}

	@Override
	public int getPriority() {
		return 70;
	}

	@Override
	public String getTitleKey() {
		return "beatblock.build_layer.clip_title";
	}

	@Override
	public boolean supports(Object target) {
		return target instanceof TimelinePropertyContext ctx && TimelinePropertyKinds.isBuildLayerClipRef(ctx.ref());
	}

	@Override
	public boolean renderProperties(TimelinePropertyContext ctx) {
		editor().renderBody(ctx.ref(), ctx.timeline(), ctx.editor());
		return false;
	}
}
