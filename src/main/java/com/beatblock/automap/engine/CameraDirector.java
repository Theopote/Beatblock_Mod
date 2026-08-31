package com.beatblock.automap.engine;

import com.beatblock.automap.camera.CameraPlanningContext;
import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.camera.CameraShotBeatAlignment;
import com.beatblock.automap.camera.CameraShotEasing;
import com.beatblock.automap.camera.CameraShotFraming;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.automap.camera.CameraShotTransition;
import com.beatblock.automap.camera.CameraCollisionPolicy;
import com.beatblock.automap.camera.CameraSubject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 自动镜头导演：根据音乐段落与风格生成带主体绑定的 {@link CameraShot}。
 * <p>
 * 示例：Orbit(StageObject A)、PushIn(StageObject B)、Overview(All)。
 */
public final class CameraDirector {

	private CameraDirector() {}

	/**
	 * 根据段落、BPM、总时长与风格生成镜头列表（legacy 入口，无 per-feature 目标映射）。
	 */
	public static List<CameraEvent> generate(List<StructuralSection> sections, float bpm,
	                                         double durationSeconds, AutoMapStyle style, boolean enabled) {
		CameraPlanningContext context = new CameraPlanningContext(bpm, durationSeconds, style, List.of());
		return generateShots(sections, context, enabled).stream().map(CameraEvent::new).toList();
	}

	public static List<CameraShot> generateShots(
		List<StructuralSection> sections,
		CameraPlanningContext context,
		boolean enabled
	) {
		List<CameraShot> out = new ArrayList<>();
		if (!enabled || sections == null || sections.isEmpty() || context == null) return out;

		double beat = context.beatDurationSeconds();
		for (int i = 0; i < sections.size(); i++) {
			StructuralSection sec = sections.get(i);
			CameraSubject primary = context.subjectForSection(i, false);
			CameraSubject overview = context.overviewSubject();
			double sectionDuration = sec.getDurationSeconds();

			switch (sec.getType()) {
				case INTRO -> {
					addShot(out, sec.getStartSeconds(), sectionDuration * 0.5,
						overview, CameraShotMovement.HOLD, CameraShotFraming.OVERVIEW, i, context, beat);
					addShot(out, sec.getStartSeconds() + sectionDuration * 0.5, sectionDuration * 0.5,
						overview, CameraShotMovement.PAN, CameraShotFraming.WIDE, i, context, beat);
				}
				case DROP, CHORUS -> {
					double orbitDuration = Math.min(beat * 4, sectionDuration * 0.6);
					addShot(out, sec.getStartSeconds(), orbitDuration,
						primary, CameraShotMovement.ORBIT, CameraShotFraming.MEDIUM, i, context, beat);
					addShot(out, sec.getStartSeconds() + beat * 4, beat * 2,
						primary, CameraShotMovement.SHAKE, CameraShotFraming.CLOSE, i, context, beat);
					addShot(out, sec.getEndSeconds() - 0.3, 0.5,
						primary, CameraShotMovement.HOLD, CameraShotFraming.MEDIUM, i, context, beat);
				}
				case PRE_CHORUS, BUILD -> {
					double pushDuration = Math.max(0.5, sectionDuration - 0.5);
					addShot(out, sec.getStartSeconds(), pushDuration,
						primary, CameraShotMovement.PUSH_IN, CameraShotFraming.MEDIUM, i, context, beat);
					addShot(out, sec.getEndSeconds() - 0.5, 0.5,
						primary, CameraShotMovement.HOLD, CameraShotFraming.CLOSE, i, context, beat);
				}
				case BRIDGE, BREAK -> {
					addShot(out, sec.getStartSeconds(), sectionDuration,
						primary, CameraShotMovement.PULL_OUT, CameraShotFraming.WIDE, i, context, beat);
				}
				case OUTRO -> {
					addShot(out, sec.getStartSeconds(), sectionDuration,
						overview, CameraShotMovement.PAN, CameraShotFraming.WIDE, i, context, beat);
					addShot(out, context.durationSeconds() - 0.5, 0.5,
						overview, CameraShotMovement.HOLD, CameraShotFraming.OVERVIEW, i, context, beat);
				}
				default -> addShot(out, sec.getStartSeconds(), Math.min(2.0, sectionDuration),
					primary, CameraShotMovement.HOLD, CameraShotFraming.MEDIUM, i, context, beat);
			}
		}

		out.sort(Comparator.comparingDouble(CameraShot::startSeconds));
		return deduplicate(out);
	}

	private static void addShot(
		List<CameraShot> out,
		double startSeconds,
		double durationSeconds,
		CameraSubject subject,
		CameraShotMovement movement,
		CameraShotFraming framing,
		int sectionIndex,
		CameraPlanningContext context,
		double beatDuration
	) {
		double start = snapToBeat(startSeconds, beatDuration);
		out.add(new CameraShot(
			start,
			Math.max(0.05, durationSeconds),
			subject,
			framing,
			movement,
			subject,
			CameraShotTransition.CUT,
			CameraShotEasing.SMOOTH,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.onBeat(),
			sectionIndex
		));
	}

	private static double snapToBeat(double timeSeconds, double beatDuration) {
		if (beatDuration <= 0) return timeSeconds;
		return Math.round(timeSeconds / beatDuration) * beatDuration;
	}

	private static List<CameraShot> deduplicate(List<CameraShot> shots) {
		List<CameraShot> dedup = new ArrayList<>();
		double lastT = -1;
		for (CameraShot shot : shots) {
			if (Math.abs(shot.startSeconds() - lastT) < 0.05) continue;
			dedup.add(shot);
			lastT = shot.startSeconds();
		}
		return dedup;
	}
}
