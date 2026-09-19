package com.beatblock.automap.choreography;

import com.beatblock.automap.camera.CameraCollisionPolicy;
import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.camera.CameraShotBeatAlignment;
import com.beatblock.automap.camera.CameraShotCodec;
import com.beatblock.automap.camera.CameraShotEasing;
import com.beatblock.automap.camera.CameraShotFraming;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.automap.camera.CameraShotTransition;
import com.beatblock.automap.camera.CameraSubject;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * BUILD_REVEAL 镜头弧：按建造进度（非仅 SectionType）编排。
 * <pre>
 * 0–30%  wide overview (HOLD → PAN)
 * 30–70% slow orbit / tracking on build layer
 * 70–95% push in
 * 95–100% hero hold
 * </pre>
 */
public final class BuildRevealCameraPlanner {

	private static final double MIN_ARC_DURATION = 1.5;

	private BuildRevealCameraPlanner() {}

	/**
	 * 用建造进度镜头覆盖各 {@link BuildSequencePlan} 时间窗内的模板镜头；窗外镜头保留。
	 */
	public static ChoreographyPlan apply(@Nullable ChoreographyPlan plan) {
		if (plan == null) {
			return ChoreographyPlan.empty();
		}
		if (plan.buildSequences().isEmpty()) {
			return plan;
		}

		List<ChoreographyPlan.CameraPhrase> kept = new ArrayList<>();
		for (ChoreographyPlan.CameraPhrase camera : plan.cameraPhrases()) {
			if (camera == null) {
				continue;
			}
			if (!overlapsAnyBuildWindow(camera, plan.buildSequences())) {
				kept.add(camera);
			}
		}

		List<ChoreographyPlan.CameraPhrase> arcs = new ArrayList<>();
		for (BuildSequencePlan sequence : plan.buildSequences()) {
			if (sequence == null || !sequence.isValid()) {
				continue;
			}
			for (CameraShot shot : planShots(sequence)) {
				arcs.add(CameraShotCodec.toPhrase(shot));
			}
		}
		arcs.sort(Comparator.comparingDouble(ChoreographyPlan.CameraPhrase::timeSeconds));

		List<ChoreographyPlan.CameraPhrase> merged = new ArrayList<>(kept.size() + arcs.size());
		merged.addAll(kept);
		merged.addAll(arcs);
		merged.sort(Comparator.comparingDouble(ChoreographyPlan.CameraPhrase::timeSeconds));

		return new ChoreographyPlan(
			plan.sections(),
			plan.stageRoles(),
			plan.motionPhrases(),
			merged,
			plan.vfxPhrases(),
			plan.densityCurve(),
			plan.sectionEdits(),
			plan.musicalStructure(),
			plan.spatialMotifPhrases(),
			plan.choreographyPhrases(),
			plan.buildSequences()
		);
	}

	/** 按建造进度生成镜头列表（供测试与直接调用）。 */
	public static List<CameraShot> planShots(BuildSequencePlan sequence) {
		if (sequence == null || !sequence.isValid()) {
			return List.of();
		}
		double start = sequence.startSeconds();
		double duration = sequence.durationSeconds();
		int sectionIndex = sequence.sectionIndex();
		CameraSubject overview = CameraSubject.allStageObjects();
		CameraSubject focus = resolveFocus(sequence);

		if (duration < MIN_ARC_DURATION) {
			return List.of(
				shot(start, duration * 0.55, overview, CameraShotMovement.HOLD, CameraShotFraming.OVERVIEW, sectionIndex),
				shot(start + duration * 0.55, duration * 0.45, focus, CameraShotMovement.PUSH_IN, CameraShotFraming.CLOSE, sectionIndex)
			);
		}

		List<CameraShot> out = new ArrayList<>(5);
		// 0–30%: overview
		double t0 = start;
		double d0 = duration * 0.30;
		out.add(shot(t0, d0 * 0.55, overview, CameraShotMovement.HOLD, CameraShotFraming.OVERVIEW, sectionIndex));
		out.add(shot(t0 + d0 * 0.55, d0 * 0.45, overview, CameraShotMovement.PAN, CameraShotFraming.WIDE, sectionIndex));

		// 30–70%: orbit / track
		double t1 = start + duration * 0.30;
		double d1 = duration * 0.40;
		out.add(shot(t1, d1, focus, CameraShotMovement.ORBIT, CameraShotFraming.MEDIUM, sectionIndex));

		// 70–95%: push in
		double t2 = start + duration * 0.70;
		double d2 = duration * 0.25;
		out.add(shot(t2, d2, focus, CameraShotMovement.PUSH_IN, CameraShotFraming.CLOSE, sectionIndex));

		// 95–100%: hero hold
		double t3 = start + duration * 0.95;
		double d3 = Math.max(0.05, duration * 0.05);
		out.add(shot(t3, d3, focus, CameraShotMovement.HOLD, CameraShotFraming.CLOSE, sectionIndex));

		return List.copyOf(out);
	}

	private static CameraSubject resolveFocus(BuildSequencePlan sequence) {
		if (sequence.layerId() != null && !sequence.layerId().isBlank()) {
			return CameraSubject.buildLayer(sequence.layerId());
		}
		if (sequence.targetObjectId() != null && !sequence.targetObjectId().isBlank()) {
			return CameraSubject.stageObject(sequence.targetObjectId());
		}
		return CameraSubject.allStageObjects();
	}

	private static CameraShot shot(
		double start,
		double duration,
		CameraSubject subject,
		CameraShotMovement movement,
		CameraShotFraming framing,
		int sectionIndex
	) {
		return new CameraShot(
			start,
			Math.max(0.05, duration),
			subject,
			framing,
			movement,
			subject,
			CameraShotTransition.SMOOTH_MOVE,
			CameraShotEasing.SMOOTH,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.none(),
			sectionIndex
		);
	}

	private static boolean overlapsAnyBuildWindow(
		ChoreographyPlan.CameraPhrase camera,
		List<BuildSequencePlan> sequences
	) {
		double camStart = camera.timeSeconds();
		double camEnd = camStart + Math.max(0.05, camera.durationSeconds());
		for (BuildSequencePlan sequence : sequences) {
			if (sequence == null || !sequence.isValid()) {
				continue;
			}
			if (camStart < sequence.endSeconds() && camEnd > sequence.startSeconds()) {
				return true;
			}
		}
		return false;
	}
}
