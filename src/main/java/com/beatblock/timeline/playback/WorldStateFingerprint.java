package com.beatblock.timeline.playback;

import com.beatblock.client.camera.TimelineCameraEvaluator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * 某一时刻舞台世界态的可比较指纹：Golden Playback Contract（play / seek / loop / export）验收用。
 * <p>
 * 覆盖 Visible blocks、Build progress、Stage/Animated labels、Camera、VFX；
 * Block states 在逻辑层以「已揭示方块集合哈希 + 进度」表示（不依赖真实 ServerWorld）。
 */
public record WorldStateFingerprint(
	double timeSeconds,
	Map<String, String> stageLabels,
	Map<String, String> globalStates,
	Map<String, BuildProgressDigest> buildProgress,
	int visibleBlockCount,
	String visibleBlocksHash,
	String animatedStateFingerprint,
	String cameraFingerprint,
	String vfxFingerprint
) {
	public WorldStateFingerprint {
		stageLabels = Map.copyOf(stageLabels != null ? stageLabels : Map.of());
		globalStates = Map.copyOf(globalStates != null ? globalStates : Map.of());
		buildProgress = Map.copyOf(buildProgress != null ? buildProgress : Map.of());
		visibleBlockCount = Math.max(0, visibleBlockCount);
		visibleBlocksHash = visibleBlocksHash != null ? visibleBlocksHash : "";
		animatedStateFingerprint = animatedStateFingerprint != null ? animatedStateFingerprint : "";
		cameraFingerprint = cameraFingerprint != null ? cameraFingerprint : "";
		vfxFingerprint = vfxFingerprint != null ? vfxFingerprint : "";
	}

	public static WorldStateFingerprint fromResolved(ResolvedStageState resolved) {
		if (resolved == null) {
			throw new IllegalArgumentException("resolved must not be null");
		}
		PlaybackStateDigest digest = resolved.toPlaybackDigest();
		List<BlockPos> visible = resolved.requiredBuildBlockPositions();
		return new WorldStateFingerprint(
			resolved.timeSeconds(),
			digest.stageStates(),
			digest.globalStates(),
			digest.buildProgress(),
			visible.size(),
			hashBlockPositions(visible),
			animatedFingerprint(digest.stageStates()),
			cameraFingerprint(resolved.camera()),
			resolved.vfxState().fingerprint()
		);
	}

	/**
	 * A：从 0 正向播放到 {@code timeSeconds}（{@link PlaybackStateDigest#playTo}），
	 * 再与 resolver 的 Camera / VFX / 可见块对齐。
	 */
	public static WorldStateFingerprint playTo(
		CompiledTimelineSnapshot program,
		double timeSeconds,
		@Nullable Vec3d cameraAnchor
	) {
		PlaybackStateDigest play = PlaybackStateDigest.playTo(program, timeSeconds);
		ResolvedStageState resolved = resolve(program, timeSeconds, cameraAnchor);
		return mergePlayDigest(play, resolved);
	}

	/** B：直接 Seek / reconstruct 到 {@code timeSeconds}。 */
	public static WorldStateFingerprint seekTo(
		CompiledTimelineSnapshot program,
		double timeSeconds,
		@Nullable Vec3d cameraAnchor
	) {
		return fromResolved(resolve(program, timeSeconds, cameraAnchor));
	}

	/** D：导出帧采样（与 {@link com.beatblock.client.export.VideoExportFrameSampler} 对齐）。 */
	public static WorldStateFingerprint fromExportFrame(
		CompiledTimelineSnapshot program,
		PlaybackStateDigest exportStageDigest,
		double timelineTimeSeconds,
		@Nullable Vec3d cameraAnchor
	) {
		ResolvedStageState resolved = resolve(program, timelineTimeSeconds, cameraAnchor);
		return mergePlayDigest(exportStageDigest, resolved);
	}

	/**
	 * C：先播放到 {@code loopOutSeconds}，再 reconstruct 回 {@code probeSeconds}
	 * （模拟 Loop wrap → RECONSTRUCT_STATE）。
	 */
	public static WorldStateFingerprint loopWrapTo(
		CompiledTimelineSnapshot program,
		double loopOutSeconds,
		double probeSeconds,
		@Nullable Vec3d cameraAnchor
	) {
		PlaybackEngine engine = new PlaybackEngine();
		engine.load(program);
		engine.advance(loopOutSeconds, (compiled, event) -> {}, ignored -> {});
		engine.seek(
			probeSeconds,
			SeekMode.RECONSTRUCT_STATE,
			(compiled, event) -> {},
			ignored -> {}
		);
		// reconstruct 语义只依赖 (program, probe)；advance 证明可从 loopOut 安全回绕
		PlaybackStateDigest afterLoop = PlaybackStateDigest.reconstructAt(program, probeSeconds);
		ResolvedStageState resolved = resolve(program, probeSeconds, cameraAnchor);
		return mergePlayDigest(afterLoop, resolved);
	}

	private static WorldStateFingerprint mergePlayDigest(
		PlaybackStateDigest digest,
		ResolvedStageState resolved
	) {
		List<BlockPos> visible = resolved.requiredBuildBlockPositions();
		return new WorldStateFingerprint(
			resolved.timeSeconds(),
			digest.stageStates(),
			digest.globalStates(),
			digest.buildProgress(),
			visible.size(),
			hashBlockPositions(visible),
			animatedFingerprint(digest.stageStates()),
			cameraFingerprint(resolved.camera()),
			resolved.vfxState().fingerprint()
		);
	}

	private static ResolvedStageState resolve(
		CompiledTimelineSnapshot program,
		double timeSeconds,
		@Nullable Vec3d cameraAnchor
	) {
		Vec3d anchor = cameraAnchor != null ? cameraAnchor : Vec3d.ZERO;
		return StageStateResolver.resolve(program, timeSeconds, anchor, 0f, 0f);
	}

	static String hashBlockPositions(List<BlockPos> positions) {
		CRC32 crc = new CRC32();
		if (positions != null) {
			for (BlockPos pos : positions) {
				if (pos == null) continue;
				crc.update(Integer.toString(pos.getX()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
				crc.update((byte) ',');
				crc.update(Integer.toString(pos.getY()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
				crc.update((byte) ',');
				crc.update(Integer.toString(pos.getZ()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
				crc.update((byte) ';');
			}
		}
		return String.format(Locale.ROOT, "%08x", crc.getValue());
	}

	private static String animatedFingerprint(Map<String, String> stageLabels) {
		return stageLabels.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(e -> e.getKey() + "=" + e.getValue())
			.collect(Collectors.joining(";"));
	}

	private static String cameraFingerprint(TimelineCameraEvaluator.@Nullable CameraSample camera) {
		if (camera == null || camera.position() == null) {
			return "";
		}
		return String.format(
			Locale.ROOT,
			"%.4f,%.4f,%.4f,%.2f,%.2f",
			camera.position().x,
			camera.position().y,
			camera.position().z,
			camera.yawDeg(),
			camera.pitchDeg()
		);
	}

	/** 紧凑诊断串，便于断言失败时阅读。 */
	public String summary() {
		StringBuilder build = new StringBuilder();
		for (var e : buildProgress.entrySet()) {
			if (build.length() > 0) build.append(',');
			build.append(e.getValue().fingerprint());
		}
		return "t=" + timeSeconds
			+ " visible=" + visibleBlockCount + "/" + visibleBlocksHash
			+ " anim=[" + animatedStateFingerprint + "]"
			+ " build=[" + build + "]"
			+ " cam=[" + cameraFingerprint + "]"
			+ " vfx=[" + vfxFingerprint + "]";
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof WorldStateFingerprint other)) return false;
		return Double.compare(timeSeconds, other.timeSeconds) == 0
			&& visibleBlockCount == other.visibleBlockCount
			&& Objects.equals(stageLabels, other.stageLabels)
			&& Objects.equals(globalStates, other.globalStates)
			&& Objects.equals(buildProgress, other.buildProgress)
			&& Objects.equals(visibleBlocksHash, other.visibleBlocksHash)
			&& Objects.equals(animatedStateFingerprint, other.animatedStateFingerprint)
			&& Objects.equals(cameraFingerprint, other.cameraFingerprint)
			&& Objects.equals(vfxFingerprint, other.vfxFingerprint);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			timeSeconds,
			stageLabels,
			globalStates,
			buildProgress,
			visibleBlockCount,
			visibleBlocksHash,
			animatedStateFingerprint,
			cameraFingerprint,
			vfxFingerprint
		);
	}
}
