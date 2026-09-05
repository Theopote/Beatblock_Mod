package com.beatblock.ui.properties.adapters;

import com.beatblock.BeatBlock;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.properties.IPropertyAdapter;
import com.beatblock.ui.properties.TimelinePropertyContext;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import com.beatblock.ui.properties.editors.AudioClipPropertyEditor;

public final class AudioClipPropertyAdapter implements IPropertyAdapter<TimelinePropertyContext> {

	private AudioClipPropertyEditor editor;
	private EventPropertiesPresenter boundPresenter;

	private AudioClipPropertyEditor editor(EventPropertiesPresenter presenter) {
		if (editor == null || boundPresenter != presenter) {
			editor = presenter != null
				? new AudioClipPropertyEditor(presenter, BeatBlock::getContext)
				: new AudioClipPropertyEditor();
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
		return 60;
	}

	@Override
	public String getTitleKey() {
		return "beatblock.audio.title";
	}

	@Override
	public boolean supports(Object target) {
		return target instanceof TimelinePropertyContext ctx && TimelinePropertyKinds.isAudioClipRef(ctx.ref());
	}

	@Override
	public boolean renderProperties(TimelinePropertyContext ctx) {
		editor(ctx.presenter()).renderBody(ctx.ref(), ctx.timeline(), ctx.editor());
		return false;
	}
}
