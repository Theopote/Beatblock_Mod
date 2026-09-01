package com.beatblock.client.export;

import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventPayload;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** 某一时间线时刻上激活的屏幕级 VFX（导出帧合成与回归测试共用）。 */
public record ExportVfxState(
	@Nullable CompiledGlobalEvent activeTint,
	@Nullable CompiledGlobalEvent activeFlash
) {
	public static ExportVfxState resolve(List<CompiledGlobalEvent> events, double timelineTimeSeconds) {
		if (!Double.isFinite(timelineTimeSeconds)) {
			throw new IllegalArgumentException("timelineTimeSeconds must be finite");
		}
		if (events == null || events.isEmpty()) {
			return new ExportVfxState(null, null);
		}

		CompiledGlobalEvent activeTint = null;
		CompiledGlobalEvent activeFlash = null;
		for (CompiledGlobalEvent event : events) {
			if (event == null || event.timeSeconds() > timelineTimeSeconds) {
				break;
			}
			if (event.payload() instanceof GlobalEventPayload.ScreenTint tint) {
				activeTint = isActive(event.timeSeconds(), tint.durationSeconds(), timelineTimeSeconds) ? event : null;
			} else if (event.payload() instanceof GlobalEventPayload.ScreenFlash flash
				&& isActive(event.timeSeconds(), Math.max(0.01, flash.durationSeconds()), timelineTimeSeconds)) {
				activeFlash = event;
			}
		}
		return new ExportVfxState(activeTint, activeFlash);
	}

	public String fingerprint() {
		return "tint=" + displayName(activeTint) + ";flash=" + displayName(activeFlash);
	}

	private static String displayName(@Nullable CompiledGlobalEvent event) {
		return event != null ? event.name() : "";
	}

	private static boolean isActive(double start, double duration, double time) {
		return duration <= 0 || time < start + duration;
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
