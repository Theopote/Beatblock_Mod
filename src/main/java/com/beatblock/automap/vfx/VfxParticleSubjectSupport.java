package com.beatblock.automap.vfx;

import com.beatblock.automap.camera.CameraSubject;
import com.beatblock.automap.camera.CameraSubjectBoundsResolver;
import com.beatblock.automap.camera.CameraSubjectKind;
import com.beatblock.automap.camera.CameraSubjectResolver;
import com.beatblock.automap.camera.CameraSubjectRole;
import com.beatblock.automap.camera.StageBounds;
import com.beatblock.timeline.playback.GlobalEventPayload;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Resolve particle burst anchors from {@link CameraSubject} (Creator + playback). */
public final class VfxParticleSubjectSupport {

	public static final String SUBJECT_ALL_ID = "";
	public static final String SUBJECT_CUSTOM_ID = "__custom__";

	private VfxParticleSubjectSupport() {
	}

	public static CameraSubject resolveSubject(@Nullable String subjectId) {
		if (subjectId == null || subjectId.isBlank()) {
			return CameraSubject.allStageObjects();
		}
		return CameraSubject.stageObject(subjectId);
	}

	public static GlobalEventPayload.ParticleBurst anchorToSubject(
		GlobalEventPayload.ParticleBurst base,
		CameraSubject subject
	) {
		Vec3d center = resolveEmissionCenter(subject);
		return new GlobalEventPayload.ParticleBurst(
			base.name(),
			base.particleType(),
			center.x,
			center.y,
			center.z,
			base.count(),
			base.spread(),
			base.speed(),
			subject.kind(),
			subject.refId()
		);
	}

	public static Vec3d resolveEmissionCenter(CameraSubject subject) {
		Optional<StageBounds> bounds = CameraSubjectBoundsResolver.tryResolve(subject);
		if (bounds.isPresent()) {
			return bounds.get().center();
		}
		return CameraSubjectResolver.resolveRequired(subject, CameraSubjectRole.SUBJECT);
	}

	public static Vec3d emissionOrigin(GlobalEventPayload.ParticleBurst payload) {
		if (payload == null) {
			return Vec3d.ZERO;
		}
		CameraSubject follow = payload.followSubject();
		if (follow != null) {
			return resolveEmissionCenter(follow);
		}
		return new Vec3d(payload.x(), payload.y(), payload.z());
	}

	public static String subjectIdFromPayload(GlobalEventPayload.ParticleBurst payload) {
		if (payload == null || payload.followSubjectKind() == null) {
			return SUBJECT_CUSTOM_ID;
		}
		if (payload.followSubjectKind() == CameraSubjectKind.STAGE_OBJECT) {
			return payload.followSubjectRef();
		}
		return SUBJECT_ALL_ID;
	}
}
