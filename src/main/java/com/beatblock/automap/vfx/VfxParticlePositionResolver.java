package com.beatblock.automap.vfx;

import com.beatblock.automap.camera.CameraSubject;
import com.beatblock.client.BeatBlockUIScreen;
import com.beatblock.client.camera.CameraKeyframeActions;
import com.beatblock.client.input.BeatBlockInputSystem;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Resolve particle burst anchor for Creator / Properties.
 * Priority: selected StageObject center → selected BuildLayer center → crosshair → manual.
 */
public final class VfxParticlePositionResolver {

	public enum Source {
		STAGE_OBJECT,
		BUILD_LAYER,
		CROSSHAIR,
		MANUAL
	}

	public record Resolved(
		Source source,
		String displayLabel,
		double x,
		double y,
		double z,
		@Nullable String anchorRef
	) {
		public @Nullable CameraSubject followSubject() {
			return switch (source) {
				case STAGE_OBJECT -> CameraSubject.stageObject(anchorRef);
				case BUILD_LAYER -> CameraSubject.buildLayer(anchorRef);
				case CROSSHAIR, MANUAL -> null;
			};
		}
	}

	private VfxParticlePositionResolver() {
	}

	public static Resolved resolve(
		@Nullable BuildLayerManager layerManager,
		@Nullable StageObjectSystem stageObjects
	) {
		return resolve(layerManager, stageObjects, VfxParticlePositionResolver::sampleCrosshairWorldPoint, null);
	}

	public static Resolved resolve(
		@Nullable BuildLayerManager layerManager,
		@Nullable StageObjectSystem stageObjects,
		Supplier<Optional<Vec3d>> crosshairSupplier,
		@Nullable Resolved manualFallback
	) {
		Resolved stageObject = resolveSelectedStageObject(layerManager, stageObjects);
		if (stageObject != null) {
			return stageObject;
		}
		Resolved buildLayer = resolveSelectedBuildLayer(layerManager);
		if (buildLayer != null) {
			return buildLayer;
		}
		Supplier<Optional<Vec3d>> supplier = crosshairSupplier != null
			? crosshairSupplier
			: VfxParticlePositionResolver::sampleCrosshairWorldPoint;
		Optional<Vec3d> crosshair = supplier.get();
		if (crosshair.isPresent()) {
			Vec3d point = crosshair.get();
			return new Resolved(
				Source.CROSSHAIR,
				BBTexts.get("beatblock.vfx_creator.position.crosshair"),
				point.x, point.y, point.z,
				null
			);
		}
		if (manualFallback != null) {
			return manualFallback;
		}
		return new Resolved(
			Source.MANUAL,
			BBTexts.get("beatblock.vfx_creator.position.manual_origin"),
			0, 64, 0,
			null
		);
	}

	public static String labelForPayload(
		GlobalEventPayload.ParticleBurst payload,
		@Nullable StageObjectSystem stageObjects,
		@Nullable BuildLayerManager layerManager
	) {
		if (payload == null) {
			return "";
		}
		if (payload.followSubjectKind() != null) {
			return switch (payload.followSubjectKind()) {
				case STAGE_OBJECT -> centerLabel(stageObjectDisplayName(
					stageObjects != null ? stageObjects.get(payload.followSubjectRef()) : null,
					payload.followSubjectRef()));
				case BUILD_LAYER -> centerLabel(buildLayerDisplayName(
					layerManager != null ? layerManager.get(payload.followSubjectRef()) : null,
					payload.followSubjectRef()));
				case ALL_STAGE_OBJECTS -> BBTexts.get("beatblock.vfx_creator.subject.all");
				default -> formatCoordinates(payload.x(), payload.y(), payload.z());
			};
		}
		return formatCoordinates(payload.x(), payload.y(), payload.z());
	}

	public static Optional<Vec3d> sampleCrosshairWorldPoint() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.world == null) {
			return Optional.empty();
		}
		BlockHitResult hit = null;
		if (mc.currentScreen instanceof BeatBlockUIScreen) {
			hit = BeatBlockInputSystem.raycastFromImGui();
		} else if (mc.crosshairTarget instanceof BlockHitResult blockHit
			&& blockHit.getType() == HitResult.Type.BLOCK) {
			hit = blockHit;
		}
		if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
			return Optional.of(hit.getPos());
		}
		return CameraKeyframeActions.sampleCurrentView()
			.map(p -> new Vec3d(p.eyeX(), p.eyeY(), p.eyeZ()));
	}

	private static @Nullable Resolved resolveSelectedStageObject(
		@Nullable BuildLayerManager layerManager,
		@Nullable StageObjectSystem stageObjects
	) {
		if (layerManager == null || stageObjects == null) {
			return null;
		}
		List<String> stageIds = layerManager.getSelectedStageObjectIds();
		if (stageIds.isEmpty()) {
			return null;
		}
		String stageId = stageIds.getFirst();
		RuntimeStageObject object = stageObjects.get(stageId);
		if (object == null) {
			return null;
		}
		Vec3d center = com.beatblock.automap.camera.StageBounds.fromStageObject(object).center();
		return new Resolved(
			Source.STAGE_OBJECT,
			centerLabel(stageObjectDisplayName(object, stageId)),
			center.x, center.y, center.z,
			stageId
		);
	}

	private static @Nullable Resolved resolveSelectedBuildLayer(@Nullable BuildLayerManager layerManager) {
		if (layerManager == null) {
			return null;
		}
		Set<String> layerIds = layerManager.getSelectedLayerIds();
		if (layerIds.isEmpty()) {
			return null;
		}
		String layerId = layerManager.getSelectionAnchorLayerId();
		if (layerId == null || !layerIds.contains(layerId)) {
			layerId = layerIds.iterator().next();
		}
		BuildLayer layer = layerManager.get(layerId);
		if (layer == null || layer.getStageObject() == null) {
			return null;
		}
		Vec3d center = com.beatblock.automap.camera.StageBounds.fromStageObject(layer.getStageObject()).center();
		return new Resolved(
			Source.BUILD_LAYER,
			centerLabel(buildLayerDisplayName(layer, layerId)),
			center.x, center.y, center.z,
			layerId
		);
	}

	private static String centerLabel(String name) {
		return BBTexts.get("beatblock.vfx_creator.position.center", name);
	}

	private static String formatCoordinates(double x, double y, double z) {
		return BBTexts.get(
			"beatblock.vfx_creator.position.manual",
			String.format(Locale.ROOT, "%.1f", x),
			String.format(Locale.ROOT, "%.1f", y),
			String.format(Locale.ROOT, "%.1f", z)
		);
	}

	private static String stageObjectDisplayName(@Nullable RuntimeStageObject object, @Nullable String id) {
		if (object != null && object.getName() != null && !object.getName().isBlank()) {
			return object.getName();
		}
		return id != null && !id.isBlank() ? id : BBTexts.get("beatblock.vfx_creator.subject.stage_object");
	}

	private static String buildLayerDisplayName(@Nullable BuildLayer layer, @Nullable String id) {
		if (layer != null && layer.getName() != null && !layer.getName().isBlank()) {
			return layer.getName();
		}
		return id != null && !id.isBlank() ? id : BBTexts.get("beatblock.vfx_creator.subject.build_layer");
	}
}
