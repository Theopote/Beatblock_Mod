package com.beatblock.timeline.command;

import com.beatblock.automap.vfx.EnvironmentPreset;
import com.beatblock.automap.vfx.GlobalEventCreationRequest;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.timeline.playback.GlobalEventPayload;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Apply a multi-cue {@link EnvironmentPreset} as one Undo step
 * ({@link CompositeCommand} of {@link CreateGlobalEventCommand}).
 */
public final class ApplyEnvironmentPresetCommand implements Command {

	private final String presetId;
	private final String presetDisplayName;
	private final List<CreateGlobalEventCommand> parts;
	private final CompositeCommand composite;

	private ApplyEnvironmentPresetCommand(
		String presetId,
		String presetDisplayName,
		List<CreateGlobalEventCommand> parts
	) {
		this.presetId = presetId != null ? presetId : "";
		this.presetDisplayName = presetDisplayName != null ? presetDisplayName : this.presetId;
		this.parts = List.copyOf(parts);
		this.composite = new CompositeCommand(this.parts.stream().map(Command.class::cast).toList());
	}

	public static ApplyEnvironmentPresetCommand of(
		Timeline timeline,
		double timeSeconds,
		EnvironmentPreset preset
	) {
		Objects.requireNonNull(timeline, "timeline");
		Objects.requireNonNull(preset, "preset");
		List<CreateGlobalEventCommand> parts = new ArrayList<>(preset.componentCount());
		for (GlobalEventPayload payload : preset.components()) {
			parts.add(new CreateGlobalEventCommand(
				timeline,
				new GlobalEventCreationRequest(timeSeconds, payload),
				TimelineGenerationMetadata.manual()
			));
		}
		return new ApplyEnvironmentPresetCommand(preset.id(), preset.displayName(), parts);
	}

	public String presetId() {
		return presetId;
	}

	public String presetDisplayName() {
		return presetDisplayName;
	}

	public int componentCount() {
		return parts.size();
	}

	public boolean wasApplied() {
		return !createdEventIds().isEmpty();
	}

	public List<String> createdClipIds() {
		List<String> ids = new ArrayList<>(parts.size());
		for (CreateGlobalEventCommand part : parts) {
			String id = part.createdClipId();
			if (id != null && !id.isBlank()) {
				ids.add(id);
			}
		}
		return List.copyOf(ids);
	}

	public List<String> createdEventIds() {
		List<String> ids = new ArrayList<>(parts.size());
		for (CreateGlobalEventCommand part : parts) {
			String id = part.createdEventId();
			if (id != null && !id.isBlank()) {
				ids.add(id);
			}
		}
		return List.copyOf(ids);
	}

	public @Nullable String firstCreatedEventId() {
		List<String> ids = createdEventIds();
		return ids.isEmpty() ? null : ids.getFirst();
	}

	@Override
	public void execute() {
		composite.execute();
	}

	@Override
	public void undo() {
		composite.undo();
	}
}
