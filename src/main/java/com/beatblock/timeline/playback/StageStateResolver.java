package com.beatblock.timeline.playback;

import com.beatblock.client.camera.TimelineCameraEvaluator;
import com.beatblock.client.export.ExportVfxState;
import com.beatblock.engine.BlockBuildOrder;
import com.beatblock.engine.BuildSequenceMode;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 舞台状态的唯一语义入口：从冻结 {@link CompiledTimelineSnapshot} 解析 time T 的期望态。
 * <p>
 * Scrub → 视觉预览；Formal rewind / Loop / Export → 权威写入；逻辑态均由此解析，
 * 避免 UI Seek 与 Formal Seek 各写一套「应该是什么」。
 */
public final class StageStateResolver {

	private StageStateResolver() {}

	public static ResolvedStageState resolve(CompiledTimelineSnapshot program, double timeSeconds) {
		return resolve(program, timeSeconds, null, 0f, 0f);
	}

	public static ResolvedStageState resolve(
		CompiledTimelineSnapshot program,
		double timeSeconds,
		@Nullable Vec3d cameraAnchor,
		float fallbackYawDeg,
		float fallbackPitchDeg
	) {
		if (program == null) {
			throw new IllegalArgumentException("program must not be null");
		}
		if (!Double.isFinite(timeSeconds)) {
			throw new IllegalArgumentException("timeSeconds must be finite");
		}
		double t = Math.max(0.0, timeSeconds);
		PlaybackStateDigest digest = PlaybackStateDigest.reconstructAt(program, t);
		ExportVfxState vfx = ExportVfxState.resolve(program.globalEvents(), t);
		List<BlockPos> requiredBlocks = collectRequiredBuildBlocks(program, t, digest.buildProgress());

		TimelineCameraEvaluator.CameraSample camera = null;
		if (cameraAnchor != null) {
			camera = TimelineCameraEvaluator.evaluate(
				program.cameraTrack(),
				program.bpm(),
				t,
				cameraAnchor,
				fallbackYawDeg,
				fallbackPitchDeg
			);
		}

		return new ResolvedStageState(
			t,
			digest.stageStates(),
			digest.buildProgress(),
			digest.globalStates(),
			vfx,
			requiredBlocks,
			camera
		);
	}

	/**
	 * 收集 time T 时 BUILD 已揭示的方块坐标（用于 Export chunk preload）。
	 */
	static List<BlockPos> collectRequiredBuildBlocks(
		CompiledTimelineSnapshot program,
		double timeSeconds,
		Map<String, BuildProgressDigest> progressByTarget
	) {
		if (program == null || progressByTarget == null || progressByTarget.isEmpty()) {
			return List.of();
		}
		List<BlockPos> out = new ArrayList<>();
		for (CompiledStageEvent compiled : program.compiledStageEvents()) {
			if (compiled == null || compiled.event() == null) continue;
			if (compiled.semantics() == PlaybackSemantics.TRANSIENT) continue;
			TimelineAnimationEvent event = compiled.event();
			if (event.getActionMode() != TimelineAnimationActionMode.BUILD) continue;
			if (event.getTimeSeconds() > timeSeconds + PlaybackEngine.EVENT_EPSILON) continue;

			CompiledStageTarget target = compiled.target();
			if (target == null || target.blocks().isEmpty()) continue;
			String key = event.getTargetObjectId().isBlank()
				? "event:" + compiled.stableSequence()
				: event.getTargetObjectId();
			BuildProgressDigest progress = progressByTarget.get(key);
			if (progress == null || progress.completedBlockCount() <= 0) continue;

			List<BlockPos> ordered = orderedBlocksForBuild(compiled);
			int n = Math.min(progress.completedBlockCount(), ordered.size());
			for (int i = 0; i < n; i++) {
				BlockPos pos = ordered.get(i);
				if (pos != null) out.add(pos.toImmutable());
			}
		}
		return List.copyOf(out);
	}

	private static List<BlockPos> orderedBlocksForBuild(CompiledStageEvent compiled) {
		TimelineAnimationEvent event = compiled.event();
		CompiledStageTarget target = compiled.target();
		String buildModeRaw = "wall";
		boolean dissolve = false;
		var payload = event.getPayload();
		if (payload instanceof com.beatblock.timeline.payload.StageEventPayload.Build build) {
			buildModeRaw = build.buildMode();
			dissolve = build.dissolve();
		}
		List<BlockPos> ordered = BlockBuildOrder.sortBlocks(
			target.blocks(),
			BuildSequenceMode.fromValue(buildModeRaw),
			target.center(),
			event,
			target.id()
		);
		if (dissolve) {
			ordered = new ArrayList<>(ordered);
			Collections.reverse(ordered);
		}
		return ordered;
	}
}
