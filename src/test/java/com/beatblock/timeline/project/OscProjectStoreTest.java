package com.beatblock.timeline.project;

import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.GlobalEvent;
import com.beatblock.timeline.GlobalEventType;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.TimelineMarker;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OscProjectStoreTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@TempDir
	Path tempDir;

	@Test
	void roundTripsProjectMetadataAndMarkers() throws Exception {
		Path file = tempDir.resolve("demo.osc");
		Timeline timeline = Timeline.createDefault();
		timeline.setName("Demo Show");
		timeline.setMetadata("audioPath", "C:/music/track.mp3");
		timeline.setMetadata("projectId", "proj-123");
		timeline.addMarker(new TimelineMarker("mk1", 12.5, "Drop", MarkerType.DROP));

		OscProjectStore.save(file, timeline);

		OscProjectStore.LoadedProject loaded = OscProjectStore.load(file);

		assertEquals("proj-123", loaded.getProjectId());
		assertEquals("Demo Show", loaded.getTimelineName());
		assertTrue(loaded.getAudioPath().replace('\\', '/').endsWith("track.mp3"));
		assertEquals(1, loaded.getMarkers().size());
		assertEquals("mk1", loaded.getMarkers().getFirst().getId());
		assertEquals(12.5, loaded.getMarkers().getFirst().getTimeSeconds(), 1e-6);
		assertEquals(MarkerType.DROP, loaded.getMarkers().getFirst().getType());
	}

	@Test
	void loadsLegacyV1JsonWithoutVersionField() throws Exception {
		Path file = tempDir.resolve("legacy.osc");
		Files.writeString(file, """
			{
			  "projectId": "legacy-id",
			  "timelineName": "Legacy",
			  "audioPath": "/audio/old.wav"
			}
			""");

		OscProjectStore.LoadedProject loaded = OscProjectStore.load(file);

		assertEquals("legacy-id", loaded.getProjectId());
		assertEquals("Legacy", loaded.getTimelineName());
		assertEquals("/audio/old.wav", loaded.getAudioPath());
		assertTrue(loaded.getMarkers().isEmpty());
	}

	@Test
	void rejectsUnsupportedFutureVersion() throws Exception {
		Path file = tempDir.resolve("future.osc");
		Files.writeString(file, """
			{"version": 99, "projectId": "x"}
			""");

		assertThrows(Exception.class, () -> OscProjectStore.load(file));
	}

	@Test
	void roundTripsBuildLayersWhenManagerProvided() throws Exception {
		Path file = tempDir.resolve("layers.osc");
		StageObjectSystem stageObjects = new StageObjectSystem();
		BlockPos pos = new BlockPos(1, 64, 2);
		StageObject stage = StageObjectSystem.fromBlocks("stage-1", "Layer Object", List.of(pos));
		stageObjects.register(stage);
		BuildLayerManager layers = new BuildLayerManager(stageObjects);
		layers.registerRestored(new BuildLayer(
			"layer-1",
			"Test Layer",
			stage,
			LayerVisibilityState.FREE_VISIBLE,
			Map.of(),
			null
		));

		Timeline timeline = Timeline.createDefault();
		timeline.setName("Layer Project");
		OscProjectStore.save(file, timeline, layers);

		StageObjectSystem restoredStages = new StageObjectSystem();
		BuildLayerManager restoredLayers = new BuildLayerManager(restoredStages);
		OscProjectStore.load(file, restoredLayers);

		assertEquals(1, restoredLayers.getAll().size());
		assertEquals("layer-1", restoredLayers.getAll().iterator().next().getId());
	}

	@Test
	void roundTripsCapturedBlockStatesInBuildLayers() throws Exception {
		Path file = tempDir.resolve("layers-capture.osc");
		BlockPos pos = new BlockPos(2, 64, 3);
		StageObjectSystem stageObjects = new StageObjectSystem();
		StageObject stage = StageObjectSystem.fromBlocks("stage-cap", "Captured", List.of(pos));
		stageObjects.register(stage);
		BuildLayerManager layers = new BuildLayerManager(stageObjects);
		layers.registerRestored(new BuildLayer(
			"layer-cap",
			"Captured Layer",
			stage,
			LayerVisibilityState.FREE_HIDDEN,
			Map.of(pos, Blocks.DIAMOND_BLOCK.getDefaultState()),
			"clip-cap"
		));

		Timeline timeline = Timeline.createDefault();
		OscProjectStore.save(file, timeline, layers);

		StageObjectSystem restoredStages = new StageObjectSystem();
		BuildLayerManager restoredLayers = new BuildLayerManager(restoredStages);
		OscProjectStore.load(file, restoredLayers);

		BuildLayer loaded = restoredLayers.getAll().iterator().next();
		assertEquals("layer-cap", loaded.getId());
		assertEquals(LayerVisibilityState.FREE_HIDDEN, loaded.getState());
		assertEquals("clip-cap", loaded.getBoundClipId());
		assertEquals(Blocks.DIAMOND_BLOCK.getDefaultState(), loaded.getCapturedStates().get(pos));
	}

	@Test
	void roundTripsAnimationTracksWhenTimelineProvided() throws Exception {
		Path file = tempDir.resolve("animation.osc");
		Timeline timeline = Timeline.createDefault();
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"ev-auto", 2.5, 1.0, "build", "stage-x", 0.9f,
			Map.of("eventOrigin", TimelineEventOrigin.AUTO_GENERATED.name(), "buildMode", "tower")));
		OscProjectStore.save(file, timeline);

		Timeline restored = Timeline.createDefault();
		OscProjectStore.load(file, null, restored);

		assertEquals(1, restored.getAutoAnimationEvents().size());
		assertEquals(2.5, restored.getAutoAnimationEvents().getFirst().getTimeSeconds(), 1e-9);
		assertEquals("stage-x", restored.getAutoAnimationEvents().getFirst().getTargetObjectId());
		assertEquals("tower", restored.getAutoAnimationEvents().getFirst().getParameters().get("buildMode"));
	}

	@Test
	void roundTripsCameraAndGlobalEventsInOsc() throws Exception {
		Path file = tempDir.resolve("camera-global.osc");
		Timeline timeline = Timeline.createDefault();
		timeline.addCameraKeyframe(new com.beatblock.timeline.CameraKeyframe(2.0));
		timeline.addGlobalEvent(new GlobalEvent(5.0, GlobalEventType.LIGHTING, "Strobe"));
		OscProjectStore.save(file, timeline);

		Timeline restored = Timeline.createDefault();
		OscProjectStore.load(file, null, restored);

		assertEquals(1, restored.getCameraKeyframes().size());
		assertEquals(1, restored.getGlobalEvents().size());
		assertEquals("Strobe", restored.getGlobalEvents().getFirst().getName());
	}

	@Test
	void saveUsesAtomicWriteAndDoesNotLeaveTempFile() throws Exception {
		Path file = tempDir.resolve("atomic.osc");
		Timeline timeline = Timeline.createDefault();
		timeline.setName("First");
		OscProjectStore.save(file, timeline);

		timeline.setName("Second");
		OscProjectStore.save(file, timeline);

		assertNoOscTempFiles(tempDir);
		OscProjectStore.LoadedProject loaded = OscProjectStore.load(file);
		assertEquals("Second", loaded.getTimelineName());
	}

	@Test
	void saveFailurePreservesOriginalFile() throws Exception {
		Path file = tempDir.resolve("preserve.osc");
		Timeline timeline = Timeline.createDefault();
		timeline.setName("Original");
		OscProjectStore.save(file, timeline);

		Timeline broken = new Timeline() {
			@Override public String getName() { throw new RuntimeException("boom"); }
		};
		assertThrows(Exception.class, () -> OscProjectStore.save(file, broken));

		OscProjectStore.LoadedProject loaded = OscProjectStore.load(file);
		assertEquals("Original", loaded.getTimelineName());
		assertNoOscTempFiles(tempDir);
	}

	@Test
	void atomicMoveNotSupportedFallsBack() throws Exception {
		Path realDir = tempDir.resolve("nonatomic");
		Files.createDirectories(realDir);
		Path realFile = realDir.resolve("fallback.osc");

		NonAtomicMoveFileSystem fs = NonAtomicMoveFileSystem.ofDefault();
		Path wrapped = fs.getPath(realFile.toString());

		// 直接测 writeAtomically：包装 FS 拒绝 ATOMIC_MOVE，应回退为普通 move
		OscProjectStore.writeAtomically(wrapped, "{\"version\":3,\"projectId\":\"fb\",\"timelineName\":\"Fallback\"}");

		assertTrue(Files.exists(realFile));
		String json = Files.readString(realFile, StandardCharsets.UTF_8);
		assertTrue(json.contains("Fallback"));
		assertNoOscTempFiles(realDir);
	}

	@Test
	void concurrentSaveUsesUniqueTempFiles() throws Exception {
		// 1) 固定名 *.osc.tmp 已被占用时，唯一临时文件仍应成功（旧实现会互相踩踏）
		Path file = tempDir.resolve("unique.osc");
		Path fixedTmp = tempDir.resolve("unique.osc.tmp");
		Files.writeString(fixedTmp, "held-by-other-saver", StandardCharsets.UTF_8);

		Timeline timeline = Timeline.createDefault();
		timeline.setName("UniqueTemp");
		OscProjectStore.save(file, timeline);

		assertEquals("UniqueTemp", OscProjectStore.load(file).getTimelineName());
		assertEquals("held-by-other-saver", Files.readString(fixedTmp, StandardCharsets.UTF_8),
			"不应再使用固定的 *.osc.tmp 名覆盖其它保存任务的临时文件");

		// 2) 同一目录下多目标并发保存：唯一临时名避免 create/write 冲突
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		try {
			List<Future<?>> futures = new java.util.ArrayList<>();
			for (int i = 0; i < threads; i++) {
				final int idx = i;
				futures.add(pool.submit(() -> {
					try {
						start.await(5, TimeUnit.SECONDS);
						Path target = tempDir.resolve("concurrent-" + idx + ".osc");
						Timeline t = Timeline.createDefault();
						t.setName("writer-" + idx);
						for (int r = 0; r < 4; r++) {
							OscProjectStore.save(target, t);
						}
					} catch (Throwable e) {
						failure.compareAndSet(null, e);
					}
				}));
			}
			start.countDown();
			for (Future<?> f : futures) {
				f.get(30, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}

		if (failure.get() != null) {
			throw new AssertionError("并发保存失败（可能是临时文件名冲突）", failure.get());
		}
		for (int i = 0; i < threads; i++) {
			Path target = tempDir.resolve("concurrent-" + i + ".osc");
			assertEquals("writer-" + i, OscProjectStore.load(target).getTimelineName());
		}
		// 允许测试预置的 fixedTmp 存在，其它 .tmp 不得残留
		try (Stream<Path> stream = Files.list(tempDir)) {
			List<Path> leftover = stream
				.filter(p -> p.getFileName().toString().endsWith(".tmp"))
				.filter(p -> !p.equals(fixedTmp))
				.toList();
			assertTrue(leftover.isEmpty(), "不应残留临时文件: " + leftover);
		}
	}

	@Test
	void failedMoveDeletesTempFile() throws Exception {
		// 目标路径已是目录时，move 会失败；应清理 createTempFile 产生的临时文件
		Path blockingDir = tempDir.resolve("blocked.osc");
		Files.createDirectories(blockingDir);

		assertThrows(Exception.class, () ->
			OscProjectStore.writeAtomically(blockingDir, "{\"version\":3,\"projectId\":\"x\"}"));

		assertNoOscTempFiles(tempDir);
		// 目录本身仍在（未被错误替换）
		assertTrue(Files.isDirectory(blockingDir));
	}

	private static void assertNoOscTempFiles(Path dir) throws Exception {
		assertFalse(Files.exists(dir.resolve(dir.getFileName() + ".tmp")));
		try (Stream<Path> stream = Files.list(dir)) {
			List<Path> temps = stream
				.filter(p -> {
					String name = p.getFileName().toString();
					return name.endsWith(".tmp") || name.contains(".osc.");
				})
				.filter(p -> p.getFileName().toString().endsWith(".tmp"))
				.toList();
			assertTrue(temps.isEmpty(), "不应残留临时文件: " + temps);
		}
	}
}
