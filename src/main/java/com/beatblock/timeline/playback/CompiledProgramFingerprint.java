package com.beatblock.timeline.playback;

import com.beatblock.timeline.TimelineAnimationEvent;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Stable SHA-256 identity of executable snapshot content. */
public final class CompiledProgramFingerprint {
	private CompiledProgramFingerprint() {}

	public static String compute(CompiledTimelineSnapshot snapshot) {
		if (snapshot == null) return sha256("null");
		StringBuilder out = new StringBuilder(4096);
		field(out, "bpm", snapshot.bpm());
		field(out, "duration", snapshot.durationSeconds());
		field(out, "restore", snapshot.restoreWorldMutations());
		for (double beat : snapshot.referenceBeatTimesSeconds()) field(out, "beat", beat);
		for (CompiledStageEvent compiled : snapshot.compiledStageEvents()) {
			TimelineAnimationEvent event = compiled.event();
			out.append("stage{");
			field(out, "sequence", compiled.stableSequence());
			if (event != null) {
				field(out, "id", event.getEventId()); field(out, "time", event.getTimeSeconds());
				field(out, "duration", event.getDurationSeconds()); field(out, "animation", event.getAnimationTypeId());
				field(out, "target", event.getTargetObjectId()); field(out, "energy", event.getEnergy());
				value(out, event.getParameters());
			}
			out.append('}');
		}
		for (var clip : snapshot.cameraTrack().clips()) {
			out.append("camera{"); field(out, "start", clip.startTimeSeconds()); field(out, "end", clip.endTimeSeconds());
			for (var event : clip.events()) {
				field(out, "id", event.id()); field(out, "time", event.timeSeconds()); field(out, "type", event.type().name());
				value(out, event.parameters());
			}
			out.append('}');
		}
		for (CompiledGlobalEvent event : snapshot.globalEvents()) {
			out.append("global{"); field(out, "id", event.id()); field(out, "time", event.timeSeconds());
			field(out, "type", event.typeName()); field(out, "name", event.name()); value(out, event.payload()); out.append('}');
		}
		for (CompiledMarker marker : snapshot.markers()) value(out, marker);
		for (CompiledBuildLayer layer : snapshot.buildLayers()) value(out, layer);
		var audio = snapshot.audio();
		field(out, "audioPath", audio.path()); field(out, "audioDuration", audio.durationSeconds()); field(out, "audioId", audio.assetId());
		return sha256(out.toString());
	}

	private static void value(StringBuilder out, Object value) {
		if (value == null) { out.append("null;"); return; }
		if (value instanceof Map<?, ?> map) {
			out.append('{'); map.entrySet().stream().sorted(Comparator.comparing(e -> String.valueOf(e.getKey())))
				.forEach(e -> { field(out, "key", e.getKey()); value(out, e.getValue()); }); out.append('}'); return;
		}
		if (value instanceof Iterable<?> values) { out.append('['); values.forEach(v -> value(out, v)); out.append(']'); return; }
		if (value.getClass().isArray()) {
			out.append('['); for (int i = 0; i < Array.getLength(value); i++) value(out, Array.get(value, i)); out.append(']'); return;
		}
		if (value.getClass().isRecord()) {
			out.append(value.getClass().getName()).append('{');
			for (RecordComponent component : value.getClass().getRecordComponents()) {
				field(out, "component", component.getName());
				try { value(out, component.getAccessor().invoke(value)); }
				catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot fingerprint record", error); }
			}
			out.append('}'); return;
		}
		field(out, value.getClass().getName(), value);
	}

	private static void field(StringBuilder out, String name, @Nullable Object value) {
		String text = value instanceof Double d ? Long.toHexString(Double.doubleToLongBits(d))
			: value instanceof Float f ? Integer.toHexString(Float.floatToIntBits(f)) : String.valueOf(value);
		out.append(name.length()).append(':').append(name).append('=').append(text.length()).append(':').append(text).append(';');
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
	}
}
