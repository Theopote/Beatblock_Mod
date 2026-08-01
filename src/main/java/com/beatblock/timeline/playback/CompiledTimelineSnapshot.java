package com.beatblock.timeline.playback;

import com.beatblock.timeline.TimelineAnimationEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Immutable compiled performance program for one formal play session.
 * <p>
 * Phase B expands the Phase A stage/camera freeze into a fuller
 * {@code CompiledPerformance}-style snapshot: build layers, audio, markers,
 * and the {@link TimelineValidationReport} produced at compile time.
 * Editing the live {@code Timeline} after compile does not affect this object.
 */
public final class CompiledTimelineSnapshot {

	private final List<TimelineAnimationEvent> stageEvents;
	private final List<CompiledStageEvent> compiledStageEvents;
	private final CompiledCameraTrack cameraTrack;
	private final List<CompiledBuildLayer> buildLayers;
	private final List<CompiledMarker> markers;
	private final CompiledAudioReference audio;
	private final double[] referenceBeatTimesSeconds;
	private final double bpm;
	private final double durationSeconds;
	private final boolean restoreWorldMutations;
	private final int sourceGeneration;
	private final @Nullable TimelineValidationReport validationReport;

	CompiledTimelineSnapshot(
		List<TimelineAnimationEvent> stageEvents,
		List<CompiledStageEvent> compiledStageEvents,
		CompiledCameraTrack cameraTrack,
		List<CompiledBuildLayer> buildLayers,
		List<CompiledMarker> markers,
		CompiledAudioReference audio,
		double[] referenceBeatTimesSeconds,
		double bpm,
		double durationSeconds,
		boolean restoreWorldMutations,
		int sourceGeneration,
		@Nullable TimelineValidationReport validationReport
	) {
		this.stageEvents = List.copyOf(stageEvents != null ? stageEvents : List.of());
		this.compiledStageEvents = List.copyOf(compiledStageEvents != null ? compiledStageEvents : List.of());
		this.cameraTrack = cameraTrack != null ? cameraTrack : new CompiledCameraTrack(List.of());
		this.buildLayers = List.copyOf(buildLayers != null ? buildLayers : List.of());
		this.markers = List.copyOf(markers != null ? markers : List.of());
		this.audio = audio != null ? audio : CompiledAudioReference.empty();
		this.referenceBeatTimesSeconds = referenceBeatTimesSeconds != null
			? referenceBeatTimesSeconds.clone()
			: new double[0];
		this.bpm = bpm;
		this.durationSeconds = durationSeconds;
		this.restoreWorldMutations = restoreWorldMutations;
		this.sourceGeneration = sourceGeneration;
		this.validationReport = validationReport;
	}

	/** Empty / invalid document compile result. */
	static CompiledTimelineSnapshot empty() {
		return new CompiledTimelineSnapshot(
			List.of(),
			List.of(),
			new CompiledCameraTrack(List.of()),
			List.of(),
			List.of(),
			CompiledAudioReference.empty(),
			new double[0],
			120.0,
			0,
			true,
			-1,
			null
		);
	}

	public List<TimelineAnimationEvent> stageEvents() {
		return stageEvents;
	}

	public List<CompiledStageEvent> compiledStageEvents() {
		return compiledStageEvents;
	}

	public CompiledCameraTrack cameraTrack() {
		return cameraTrack;
	}

	public List<CompiledBuildLayer> buildLayers() {
		return buildLayers;
	}

	public List<CompiledMarker> markers() {
		return markers;
	}

	public CompiledAudioReference audio() {
		return audio;
	}

	public double[] referenceBeatTimesSeconds() {
		return referenceBeatTimesSeconds.clone();
	}

	public double bpm() {
		return bpm;
	}

	public double durationSeconds() {
		return durationSeconds;
	}

	public boolean restoreWorldMutations() {
		return restoreWorldMutations;
	}

	public int sourceGeneration() {
		return sourceGeneration;
	}

	/** Validation report captured at compile time; may be null for legacy empty snapshots. */
	public @Nullable TimelineValidationReport validationReport() {
		return validationReport;
	}

	/** Convenience: true when compile-time validation found errors. */
	public boolean hasValidationErrors() {
		return validationReport != null && validationReport.hasErrors();
	}
}
