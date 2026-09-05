package com.beatblock.ui.properties.adapters;

import com.beatblock.BeatBlock;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.properties.IPropertyAdapter;
import com.beatblock.ui.properties.TimelinePropertyContext;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import com.beatblock.ui.properties.editors.BuildLayerClipPropertyEditor;

public final class BuildLayerClipPropertyAdapter implements IPropertyAdapter<TimelinePropertyContext> {

	private BuildLayerClipPropertyEditor editor;
	private EventPropertiesPresenter boundPresenter;

	private BuildLayerClipPropertyEditor editor(EventPropertiesPresenter presenter) {
		if (editor == null || boundPresenter != presenter) {
			editor = presenter != null
				? new BuildLayerClipPropertyEditor(presenter, BeatBlock::getContext)
				: new BuildLayerClipPropertyEditor();
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
		editor(ctx.presenter()).renderBody(ctx.ref(), ctx.timeline(), ctx.editor());
		return false;
	}
}
