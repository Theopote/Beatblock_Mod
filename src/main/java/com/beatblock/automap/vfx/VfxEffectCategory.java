package com.beatblock.automap.vfx;

/**
 * Creator category tabs — one tab per creator entry (no vague shared "Lighting").
 */
public enum VfxEffectCategory {
	ENVIRONMENT_LIGHTING(GlobalEffectKind.ENVIRONMENT_LIGHTING),
	SCREEN_TINT(GlobalEffectKind.SCREEN_TINT),
	SCREEN_FLASH(GlobalEffectKind.SCREEN_FLASH),
	WEATHER(GlobalEffectKind.WEATHER),
	PARTICLES(GlobalEffectKind.PARTICLE_BURST),
	AUDIO(GlobalEffectKind.AUDIO_MIX);

	private final GlobalEffectKind[] kinds;

	VfxEffectCategory(GlobalEffectKind... kinds) {
		this.kinds = kinds.length > 0 ? kinds : new GlobalEffectKind[]{GlobalEffectKind.SCREEN_TINT};
	}

	public GlobalEffectKind[] kinds() {
		return kinds.clone();
	}

	public GlobalEffectKind defaultKind() {
		return kinds[0];
	}

	public boolean contains(GlobalEffectKind kind) {
		if (kind == null) {
			return false;
		}
		for (GlobalEffectKind candidate : kinds) {
			if (candidate == kind) {
				return true;
			}
		}
		return false;
	}

	public static VfxEffectCategory forKind(GlobalEffectKind kind) {
		for (VfxEffectCategory category : values()) {
			if (category.contains(kind)) {
				return category;
			}
		}
		return SCREEN_TINT;
	}
}
