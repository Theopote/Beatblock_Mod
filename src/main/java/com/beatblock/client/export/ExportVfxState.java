package com.beatblock.client.export;

import com.beatblock.automap.vfx.ActiveGlobalEffectState;
import com.beatblock.timeline.playback.CompiledGlobalEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Active screen VFX at a timeline time — thin view over {@link ActiveGlobalEffectState}
 * (typed {@link com.beatblock.timeline.playback.GlobalEventPayload} only).
 */
public record ExportVfxState(
	@Nullable CompiledGlobalEvent activeTint,
	@Nullable CompiledGlobalEvent activeFlash
) {
	public static ExportVfxState resolve(List<CompiledGlobalEvent> events, double timelineTimeSeconds) {
		if (!Double.isFinite(timelineTimeSeconds)) {
			throw new IllegalArgumentException("timelineTimeSeconds must be finite");
		}
		ActiveGlobalEffectState active = ActiveGlobalEffectState.resolve(events, timelineTimeSeconds);
		return new ExportVfxState(active.screenTint(), active.screenFlash());
	}

	public String fingerprint() {
		return "tint=" + displayName(activeTint) + ";flash=" + displayName(activeFlash);
	}

	private static String displayName(@Nullable CompiledGlobalEvent event) {
		return event != null ? event.name() : "";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ExportVfxState other)) {
			return false;
		}
		return Objects.equals(displayName(activeTint), displayName(other.activeTint))
			&& Objects.equals(displayName(activeFlash), displayName(other.activeFlash));
	}

	@Override
	public int hashCode() {
		return Objects.hash(displayName(activeTint), displayName(activeFlash));
	}
}
