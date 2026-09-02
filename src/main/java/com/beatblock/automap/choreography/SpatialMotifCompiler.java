package com.beatblock.automap.choreography;

import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将 {@link SpatialMotifPhrase} 展开为带时间偏移的多目标动画事件草稿。
 */
public final class SpatialMotifCompiler {

	private SpatialMotifCompiler() {}

	public record ExpandedEvent(
		String targetObjectId,
		double timeSeconds,
		String primitiveId,
		float energy,
		double durationSeconds,
		Map<String, Object> params
	) {
		public ExpandedEvent {
			if (targetObjectId == null) targetObjectId = "";
			if (primitiveId == null) primitiveId = "";
			params = params != null ? Map.copyOf(params) : Map.of();
		}
	}

	public static List<ExpandedEvent> expand(
		SpatialMotifPhrase phrase,
		SpatialMotifLayout layout
	) {
		if (phrase == null || phrase.participantIds().size() < 2) return List.of();
		SpatialMotifLayout resolvedLayout = layout != null ? layout : SpatialMotifLayout.synthetic(
			phrase.participantIds(),
			phrase.axis()
		);
		List<RankedParticipant> ranked = rankParticipants(phrase, resolvedLayout);
		if (ranked.size() < 2) return List.of();

		Vec3d centroid = centroid(ranked);
		List<ExpandedEvent> out = new ArrayList<>(ranked.size());
		for (int i = 0; i < ranked.size(); i++) {
			RankedParticipant participant = ranked.get(i);
			double delay = delaySeconds(phrase, participant, i, ranked, centroid);
			String primitive = resolvePrimitive(phrase, i);
			float energy = resolveEnergy(phrase, i, ranked.size());
			Map<String, Object> params = baseParams(phrase, energy);
			params.put("spatialMotifId", phrase.motifId().name());
			params.put("spatialMotifIndex", i);
			out.add(new ExpandedEvent(
				participant.id(),
				phrase.timeSeconds() + delay,
				primitive,
				energy,
				phrase.durationSeconds(),
				params
			));
		}
		return List.copyOf(out);
	}

	private static Map<String, Object> baseParams(SpatialMotifPhrase phrase, float energy) {
		Map<String, Object> params = new HashMap<>();
		params.put("energy", energy);
		if (phrase.useEnergyForHeight()) {
			params.put("height", energy * phrase.heightMultiplier());
		}
		return params;
	}

	private static String resolvePrimitive(SpatialMotifPhrase phrase, int index) {
		if (phrase.phaseMode() == MotifPhaseMode.COUNTERPOINT && index % 2 == 1) {
			return counterPrimitive(phrase.primitiveId());
		}
		return phrase.primitiveId();
	}

	private static float resolveEnergy(SpatialMotifPhrase phrase, int index, int participantCount) {
		float base = phrase.energy();
		if (phrase.phaseMode() == MotifPhaseMode.ALTERNATE && index % 2 == 1) {
			return Math.max(0f, Math.min(1f, base * 0.75f));
		}
		double falloff = participantCount <= 1 ? 0.0 : (double) index / (participantCount - 1);
		return switch (phrase.motifId()) {
			case RIPPLE -> Math.max(0f, Math.min(1f, base * (float) (1.0 - 0.2 * falloff)));
			case CHASE -> Math.max(0f, Math.min(1f, base * (float) (1.0 - 0.12 * falloff)));
			case EXPLODE -> index == participantCount - 1
				? Math.max(0f, Math.min(1f, base * 1.15f))
				: base;
			default -> base;
		};
	}

	private static String counterPrimitive(String primitiveId) {
		String normalized = primitiveId != null ? primitiveId.toLowerCase(Locale.ROOT) : "pulse";
		return switch (normalized) {
			case "rise" -> "pulse";
			case "jump", "bounce" -> "pulse";
			case "pulse" -> "rise";
			default -> primitiveId != null && !primitiveId.isBlank() ? primitiveId : "pulse";
		};
	}

	private static double delaySeconds(
		SpatialMotifPhrase phrase,
		RankedParticipant participant,
		int rankIndex,
		List<RankedParticipant> ranked,
		Vec3d centroid
	) {
		double step = phrase.propagationDelaySeconds();
		int participantCount = ranked.size();
		return switch (phrase.motifId()) {
			case CASCADE, CONVERGE, GATHER, ECHO -> rankIndex * step;
			case DIVERGE, EXPLODE -> rankIndex * step;
			case WAVE -> {
				double phase = participantCount <= 1 ? 0.0 : (double) rankIndex / (participantCount - 1);
				yield phase * step * Math.max(1, participantCount - 1);
			}
			case ALTERNATE -> (rankIndex % 2) * step;
			case RIPPLE -> normalizedRadialDelay(participant, centroid, ranked, step);
			case SWEEP -> normalizedAxisDelay(participant, ranked, phrase.axis(), step);
			case CHASE -> rankIndex * step * 1.08;
			case SPIRAL -> rankIndex * step * 0.92;
		};
	}

	private static double normalizedRadialDelay(
		RankedParticipant participant,
		Vec3d centroid,
		List<RankedParticipant> ranked,
		double step
	) {
		double dist = horizontalDistance(participant.center(), centroid);
		double min = ranked.stream().mapToDouble(p -> horizontalDistance(p.center(), centroid)).min().orElse(0.0);
		double max = ranked.stream().mapToDouble(p -> horizontalDistance(p.center(), centroid)).max().orElse(0.0);
		double span = Math.max(1e-6, max - min);
		double normalized = (dist - min) / span;
		return normalized * step * Math.max(1, ranked.size() - 1);
	}

	private static double normalizedAxisDelay(
		RankedParticipant participant,
		List<RankedParticipant> ranked,
		MotifAxis axis,
		double step
	) {
		double value = axisProjection(participant.center(), axis);
		double min = ranked.stream().mapToDouble(p -> axisProjection(p.center(), axis)).min().orElse(value);
		double max = ranked.stream().mapToDouble(p -> axisProjection(p.center(), axis)).max().orElse(value);
		double span = Math.max(1e-6, max - min);
		double normalized = (value - min) / span;
		return normalized * step * Math.max(1, ranked.size() - 1);
	}

	private static List<RankedParticipant> rankParticipants(
		SpatialMotifPhrase phrase,
		SpatialMotifLayout layout
	) {
		List<RankedParticipant> ranked = new ArrayList<>();
		for (String id : phrase.participantIds()) {
			if (id == null || id.isBlank()) continue;
			Vec3d center = layout.centerOf(id);
			if (center == null) {
				center = Vec3d.ZERO;
			}
			ranked.add(new RankedParticipant(id, center));
		}
		if (ranked.isEmpty()) return List.of();

		Vec3d centroid = centroid(ranked);
		Comparator<RankedParticipant> comparator = switch (phrase.motifId()) {
			case CASCADE, SWEEP -> axisComparator(phrase.axis());
			case CONVERGE, GATHER -> Comparator
				.comparingDouble((RankedParticipant p) -> distance(p.center(), centroid))
				.reversed();
			case DIVERGE, EXPLODE, RIPPLE -> Comparator.comparingDouble(p -> distance(p.center(), centroid));
			case WAVE -> axisComparator(phrase.axis());
			case ALTERNATE, ECHO -> Comparator.comparingInt(p -> phrase.participantIds().indexOf(p.id()));
			case CHASE -> chaseComparator(phrase);
			case SPIRAL -> spiralComparator(centroid, ranked);
		};
		ranked.sort(comparator);
		return ranked;
	}

	private static Comparator<RankedParticipant> chaseComparator(SpatialMotifPhrase phrase) {
		String leaderId = phrase.participantIds().isEmpty() ? "" : phrase.participantIds().getFirst();
		MotifAxis axis = phrase.axis() != null ? phrase.axis() : MotifAxis.X;
		return (left, right) -> {
			if (leaderId.equals(left.id())) return -1;
			if (leaderId.equals(right.id())) return 1;
			return Double.compare(axisProjection(left.center(), axis), axisProjection(right.center(), axis));
		};
	}

	private static Comparator<RankedParticipant> spiralComparator(
		Vec3d centroid,
		List<RankedParticipant> ranked
	) {
		double anchorAngle = spiralAnchorAngle(centroid, ranked);
		return Comparator
			.comparingDouble((RankedParticipant p) -> spiralAngleOffset(centroid, p.center(), anchorAngle))
			.thenComparingDouble(p -> horizontalDistance(p.center(), centroid));
	}

	private static double spiralAnchorAngle(Vec3d centroid, List<RankedParticipant> ranked) {
		RankedParticipant anchor = ranked.stream()
			.max(Comparator.comparingDouble(p -> p.center().x - centroid.x))
			.orElse(ranked.getFirst());
		return rawSpiralAngle(centroid, anchor.center());
	}

	private static double spiralAngleOffset(Vec3d centroid, Vec3d point, double anchorAngle) {
		double angle = rawSpiralAngle(centroid, point);
		double delta = angle - anchorAngle;
		if (delta < 0.0) {
			delta += Math.PI * 2.0;
		}
		return delta;
	}

	/** Minecraft 平面：+X = east，-Z = north，逆时针为正向。 */
	private static double rawSpiralAngle(Vec3d centroid, Vec3d point) {
		double dx = point.x - centroid.x;
		double dz = point.z - centroid.z;
		double angle = Math.atan2(-dz, dx);
		if (angle < 0.0) {
			angle += Math.PI * 2.0;
		}
		return angle;
	}

	private static Comparator<RankedParticipant> axisComparator(MotifAxis axis) {
		MotifAxis resolved = axis != null ? axis : MotifAxis.X;
		return Comparator.comparingDouble(p -> axisProjection(p.center(), resolved));
	}

	private static double axisProjection(Vec3d center, MotifAxis axis) {
		MotifAxis resolved = axis != null ? axis : MotifAxis.X;
		return switch (resolved) {
			case X -> center.x;
			case Z -> center.z;
			case RADIAL -> horizontalDistance(center, Vec3d.ZERO);
		};
	}

	private static Vec3d centroid(List<RankedParticipant> participants) {
		double x = 0.0;
		double y = 0.0;
		double z = 0.0;
		for (RankedParticipant participant : participants) {
			x += participant.center().x;
			y += participant.center().y;
			z += participant.center().z;
		}
		double n = participants.size();
		return new Vec3d(x / n, y / n, z / n);
	}

	private static double distance(Vec3d a, Vec3d b) {
		return a.distanceTo(b);
	}

	private static double horizontalDistance(Vec3d a, Vec3d b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private record RankedParticipant(String id, Vec3d center) {}
}
