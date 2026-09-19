package com.beatblock.timeline.playback;

import com.beatblock.engine.BlockBuildOrder;
import com.beatblock.engine.BuildSequenceMode;
import com.beatblock.engine.BuildSequencer;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * BUILD 逻辑进度摘要：记录目标、已完成块数与可见方块集合指纹，
 * 用于区分「事件已调度」与「世界应呈现的建造进度」。
 */
public record BuildProgressDigest(
	String targetId,
	String eventId,
	int completedBlockCount,
	int totalBlockCount,
	String visibleBlockHash
) {
	public BuildProgressDigest {
		targetId = targetId != null ? targetId : "";
		eventId = eventId != null ? eventId : "";
		completedBlockCount = Math.max(0, completedBlockCount);
		totalBlockCount = Math.max(0, totalBlockCount);
		visibleBlockHash = visibleBlockHash != null ? visibleBlockHash : "";
	}

	public String fingerprint() {
		return targetId + "|" + eventId + "|" + completedBlockCount + "/" + totalBlockCount + "|" + visibleBlockHash;
	}

	/**
	 * 从冻结编译快照推导目标时刻的 BUILD 期望进度（不读 live Timeline / 不写世界）。
	 */
	public static Map<String, BuildProgressDigest> fromProgram(CompiledTimelineSnapshot program, double timeSeconds) {
		if (program == null) {
			return Map.of();
		}
		Map<String, BuildProgressDigest> out = new LinkedHashMap<>();
		double[] beats = program.referenceBeatTimesSeconds();
		double bpm = program.bpm();
		for (CompiledStageEvent compiled : program.compiledStageEvents()) {
			if (compiled == null || compiled.event() == null) continue;
			if (compiled.semantics() == PlaybackSemantics.TRANSIENT) continue;
			TimelineAnimationEvent event = compiled.event();
			if (event.getActionMode() != TimelineAnimationActionMode.BUILD) continue;
			if (event.getTimeSeconds() > timeSeconds + PlaybackEngine.EVENT_EPSILON) continue;

			BuildProgressDigest digest = forBuildEvent(compiled, timeSeconds, beats, bpm);
			if (digest == null) continue;
			String key = digest.targetId().isBlank()
				? "event:" + compiled.stableSequence()
				: digest.targetId();
			out.put(key, digest);
		}
		return Map.copyOf(out);
	}

	static BuildProgressDigest forBuildEvent(
		CompiledStageEvent compiled,
		double timeSeconds,
		double[] beats,
		double bpm
	) {
		TimelineAnimationEvent event = compiled.event();
		CompiledStageTarget target = compiled.target();
		List<BlockPos> blocks = target != null ? target.blocks() : List.of();
		int total = blocks.size();
		if (total <= 0) {
			return new BuildProgressDigest(
				event.getTargetObjectId(),
				event.getEventId(),
				0,
				0,
				"empty"
			);
		}

		String buildModeRaw = "wall";
		boolean dissolve = false;
		var payload = event.getPayload();
		if (payload instanceof com.beatblock.timeline.payload.StageEventPayload.Build build) {
			buildModeRaw = build.buildMode();
			dissolve = build.dissolve();
		}

		List<BlockPos> ordered = BlockBuildOrder.sortBlocks(
			blocks,
			BuildSequenceMode.fromValue(buildModeRaw),
			target.center(),
			event,
			target.id()
		);
		if (dissolve) {
			ordered = new java.util.ArrayList<>(ordered);
			java.util.Collections.reverse(ordered);
		}

		double start = event.getTimeSeconds();
		double end = start + Math.max(0.05, event.getDurationSeconds());
		List<Double> timestamps = BuildSequencer.computeBlockTimestamps(total, start, end, beats, bpm);
		int completed;
		if (timestamps != null && !timestamps.isEmpty()) {
			completed = BuildSequencer.countRevealedBlocks(timestamps, timeSeconds, total);
			if (completed < 0) {
				completed = BlockBuildOrder.computeTargetBlockCount(total, start, end, timeSeconds);
			}
		} else {
			completed = BlockBuildOrder.computeTargetBlockCount(total, start, end, timeSeconds);
		}
		completed = Math.max(0, Math.min(total, completed));
		String hash = hashVisiblePrefix(ordered, completed);
		return new BuildProgressDigest(event.getTargetObjectId(), event.getEventId(), completed, total, hash);
	}

	static String hashVisiblePrefix(List<BlockPos> ordered, int completed) {
		CRC32 crc = new CRC32();
		int n = Math.max(0, Math.min(completed, ordered != null ? ordered.size() : 0));
		for (int i = 0; i < n; i++) {
			BlockPos pos = ordered.get(i);
			if (pos == null) continue;
			crc.update(Integer.toString(pos.getX()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			crc.update((byte) ',');
			crc.update(Integer.toString(pos.getY()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			crc.update((byte) ',');
			crc.update(Integer.toString(pos.getZ()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			crc.update((byte) ';');
		}
		return String.format(Locale.ROOT, "%08x", crc.getValue());
	}
}
