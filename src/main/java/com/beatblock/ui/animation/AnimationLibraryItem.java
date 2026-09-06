package com.beatblock.ui.animation;

import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.influence.InfluenceDimension;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * UI display model for Animation Library rows.
 * <p>
 * Wraps {@link AnimationDefinition} so panels depend on a catalog/presentation
 * surface instead of {@code BlockInfluencePreset} directly. Future fields
 * (category, tags, preview, constraints) should land here or on
 * {@link AnimationDefinition}, not in ImGui panels.
 */
public final class AnimationLibraryItem {

	private final AnimationDefinition definition;

	public AnimationLibraryItem(AnimationDefinition definition) {
		this.definition = Objects.requireNonNull(definition, "definition");
	}

	public static @Nullable AnimationLibraryItem from(@Nullable AnimationDefinition definition) {
		return definition != null ? new AnimationLibraryItem(definition) : null;
	}

	public String id() {
		return definition.getId();
	}

	public String displayName() {
		return definition.getName();
	}

	public float defaultDurationSeconds() {
		return definition.getDurationSeconds();
	}

	public InfluenceDimension primaryDimension() {
		return definition.getPrimaryDimension();
	}

	/** Underlying definition for apply / channel preview bridges. */
	public AnimationDefinition definition() {
		return definition;
	}
}
