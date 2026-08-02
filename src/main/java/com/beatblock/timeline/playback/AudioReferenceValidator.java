package com.beatblock.timeline.playback;

import java.nio.file.Files;
import java.nio.file.Path;

final class AudioReferenceValidator implements TimelineValidationRule {
	@Override
	public void validate(TimelineCompileContext context, DiagnosticCollector diagnostics) {
		Object raw = context.document().getMetadata("audioPath");
		String path = raw != null ? String.valueOf(raw).trim() : "";
		if (context.animationEventCount() > 0 && path.isBlank()) {
			diagnostics.add(TimelineDiagnostic.warning(TimelineValidator.RULE_MISSING_AUDIO,
				"No audio asset path bound to the timeline", null, Double.NaN));
			return;
		}
		if (path.isBlank()) return;
		boolean exists;
		try {
			exists = Files.isRegularFile(Path.of(path));
		} catch (RuntimeException ignored) {
			exists = false;
		}
		if (!exists) {
			diagnostics.add(TimelineDiagnostic.warning(TimelineValidator.RULE_AUDIO_FILE_MISSING,
				"Audio path does not exist on disk: " + path, null, Double.NaN));
		}
	}
}