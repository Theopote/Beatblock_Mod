package com.beatblock.ui.eventlibrary;

import org.jspecify.annotations.NonNull;

/**
 * Display / apply model for Event Library rows: template + health.
 */
public record EventTemplateItem(
	@NonNull EventTemplate template,
	@NonNull EventTemplateStatus status,
	@NonNull String warning
) {
	public EventTemplateItem {
		if (template == null) {
			throw new IllegalArgumentException("template");
		}
		status = status != null ? status : EventTemplateStatus.INVALID_PARAMETERS;
		warning = warning != null ? warning : "";
	}

	public boolean canApply() {
		return status == EventTemplateStatus.VALID || status == EventTemplateStatus.LEGACY;
	}

	public @NonNull String id() {
		return template.id();
	}
}
