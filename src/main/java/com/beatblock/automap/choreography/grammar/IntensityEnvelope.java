package com.beatblock.automap.choreography.grammar;

/** 跨多次触发的能量包络（Phase 1：FLAT / CRESCENDO / DECAY）。 */
public record IntensityEnvelope(
	float startEnergy,
	float endEnergy,
	EnvelopeCurve curve
) {
	public IntensityEnvelope {
		startEnergy = clamp(startEnergy);
		endEnergy = clamp(endEnergy);
		curve = curve != null ? curve : EnvelopeCurve.FLAT;
	}

	public static IntensityEnvelope flat(float energy) {
		float e = clamp(energy);
		return new IntensityEnvelope(e, e, EnvelopeCurve.FLAT);
	}

	public static IntensityEnvelope crescendo(float from, float to) {
		return new IntensityEnvelope(from, to, EnvelopeCurve.CRESCENDO);
	}

	public float sample(int triggerIndex, int triggerCount) {
		if (curve == EnvelopeCurve.FLAT || triggerCount <= 1) {
			return startEnergy;
		}
		double t = (double) triggerIndex / (triggerCount - 1);
		return clamp(startEnergy + (float) (t * (endEnergy - startEnergy)));
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}

	public enum EnvelopeCurve {
		FLAT,
		CRESCENDO,
		DECAY
	}
}
