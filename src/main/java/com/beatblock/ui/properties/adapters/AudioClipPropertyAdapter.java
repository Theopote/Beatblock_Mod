package com.beatblock.ui.properties.adapters;

import com.beatblock.ui.properties.IPropertyAdapter;
import com.beatblock.ui.properties.TimelinePropertyContext;
import com.beatblock.ui.properties.TimelinePropertyKinds;
import com.beatblock.ui.properties.editors.AudioClipPropertyEditor;

public final class AudioClipPropertyAdapter implements IPropertyAdapter<TimelinePropertyContext> {

	private AudioClipPropertyEditor editor;

	private AudioClipPropertyEditor editor() {
		if (editor == null) {
			editor = new AudioClipPropertyEditor();
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
		editor().renderBody(ctx.ref(), ctx.timeline(), ctx.editor());
		return false;
	}
}
