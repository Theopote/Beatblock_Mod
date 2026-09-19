package com.beatblock.automap.golden;

import com.beatblock.audio.analysis.AudioFeatureTimeline;
import com.beatblock.audio.analysis.BeatGrid;
import com.beatblock.audio.analysis.DetectedBeat;
import com.beatblock.audio.analysis.EnergyFrame;
import com.beatblock.audio.analysis.FrequencyBands;
import com.beatblock.audio.analysis.WaveformExtractor;
import com.beatblock.audio.analysis.structure.BarGridBuilder;
import com.beatblock.audio.analysis.structure.MusicStructure;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 人工特征 fixture：四类歌曲骨架，结构标签显式给定（不依赖 analyzer 稳定性）。
 */
public enum SongStyleFixture {
	/** 四拍强 Drop：稀疏 Intro → Build → Drop → Break → Drop → Outro。 */
	EDM,
	/** Verse / Chorus 重复明显。 */
	POP,
	/** 慢起 → Crescendo → Climax。 */
	CINEMATIC,
	/** 高密度鼓点，段落变化弱。 */
	PERCUSSIVE;

	public record Bundle(
		SongStyleFixture style,
		AudioFeatureTimeline timeline,
		MusicStructure structure
	) {}

	public Bundle build() {
		return switch (this) {
			case EDM -> edm();
			case POP -> pop();
			case CINEMATIC -> cinematic();
			case PERCUSSIVE -> percussive();
		};
	}

	private static Bundle edm() {
		float bpm = 128f;
		double duration = 64.0;
		List<StructuralSection> sections = List.of(
			section(0, 8, SectionType.INTRO),
			section(8, 16, SectionType.BUILD),
			section(16, 32, SectionType.DROP),
			section(32, 40, SectionType.BREAK),
			section(40, 56, SectionType.DROP),
			section(56, 64, SectionType.OUTRO)
		);
		return assemble(EDM, duration, bpm, sections, (t, type) -> {
			float energy = switch (type) {
				case INTRO -> 0.25f;
				case BUILD -> 0.35f + (float) ((t - 8) / 8.0) * 0.35f;
				case DROP -> 0.95f;
				case BREAK -> 0.30f;
				case OUTRO -> 0.20f;
				default -> 0.5f;
			};
			float low = type == SectionType.DROP ? 0.85f : 0.35f;
			float mid = type == SectionType.DROP ? 0.55f : 0.30f;
			float high = type == SectionType.DROP ? 0.70f : (type == SectionType.BUILD ? 0.45f : 0.20f);
			return new Sample(energy, low, mid, high, type == SectionType.DROP ? 0.95f : 0.55f);
		}, phrasesFor(sections, 0.2));
	}

	private static Bundle pop() {
		float bpm = 100f;
		double duration = 64.0;
		List<StructuralSection> sections = List.of(
			section(0, 8, SectionType.INTRO),
			section(8, 24, SectionType.VERSE),
			section(24, 40, SectionType.CHORUS),
			section(40, 48, SectionType.VERSE),
			section(48, 56, SectionType.CHORUS),
			section(56, 64, SectionType.OUTRO)
		);
		List<MusicStructure.PhraseSpan> phrases = List.of(
			new MusicStructure.PhraseSpan(8, 16, 0, 0.1),
			new MusicStructure.PhraseSpan(16, 24, 1, 0.85),
			new MusicStructure.PhraseSpan(24, 32, 2, 0.15),
			new MusicStructure.PhraseSpan(32, 40, 3, 0.80),
			new MusicStructure.PhraseSpan(40, 48, 4, 0.90),
			new MusicStructure.PhraseSpan(48, 56, 5, 0.88)
		);
		return assemble(POP, duration, bpm, sections, (t, type) -> {
			float energy = switch (type) {
				case INTRO -> 0.30f;
				case VERSE -> 0.45f;
				case CHORUS -> 0.80f;
				case OUTRO -> 0.25f;
				default -> 0.5f;
			};
			float low = type == SectionType.CHORUS ? 0.55f : 0.40f;
			float mid = type == SectionType.CHORUS ? 0.60f : 0.45f;
			float high = type == SectionType.CHORUS ? 0.50f : 0.28f;
			return new Sample(energy, low, mid, high, type == SectionType.CHORUS ? 0.85f : 0.6f);
		}, phrases);
	}

	private static Bundle cinematic() {
		float bpm = 80f;
		double duration = 64.0;
		List<StructuralSection> sections = List.of(
			section(0, 16, SectionType.INTRO),
			section(16, 40, SectionType.BUILD),
			section(40, 52, SectionType.CHORUS),
			section(52, 64, SectionType.OUTRO)
		);
		return assemble(CINEMATIC, duration, bpm, sections, (t, type) -> {
			float energy = switch (type) {
				case INTRO -> 0.15f;
				case BUILD -> 0.20f + (float) ((t - 16) / 24.0) * 0.55f;
				case CHORUS -> 0.88f;
				case OUTRO -> 0.18f;
				default -> 0.4f;
			};
			float low = type == SectionType.CHORUS ? 0.70f : 0.25f + energy * 0.3f;
			float mid = type == SectionType.CHORUS ? 0.50f : 0.22f;
			float high = type == SectionType.CHORUS ? 0.40f : 0.12f + energy * 0.2f;
			return new Sample(energy, low, mid, high, Math.max(0.35f, energy));
		}, phrasesFor(sections, 0.15));
	}

	private static Bundle percussive() {
		float bpm = 140f;
		double duration = 32.0;
		List<StructuralSection> sections = List.of(
			section(0, 4, SectionType.INTRO),
			section(4, 28, SectionType.VERSE),
			section(28, 32, SectionType.OUTRO)
		);
		return assemble(PERCUSSIVE, duration, bpm, sections, (t, type) -> {
			// dense mid/high drum texture
			float pulse = (Math.floor(t * 8) % 2 == 0) ? 0.85f : 0.55f;
			float energy = type == SectionType.VERSE ? pulse : 0.35f;
			return new Sample(energy, 0.50f, 0.75f, 0.80f, 0.9f);
		}, phrasesFor(sections, 0.5));
	}

	@FunctionalInterface
	private interface Sampler {
		Sample sample(double timeSeconds, SectionType type);
	}

	private record Sample(float energy, float low, float mid, float high, float beatStrength) {}

	private static Bundle assemble(
		SongStyleFixture style,
		double duration,
		float bpm,
		List<StructuralSection> sections,
		Sampler sampler,
		List<MusicStructure.PhraseSpan> phrases
	) {
		List<EnergyFrame> energyFrames = new ArrayList<>();
		List<FrequencyBands> bands = new ArrayList<>();
		List<DetectedBeat> beats = new ArrayList<>();
		double beatDur = 60.0 / bpm;

		for (double t = 0; t <= duration + 1e-9; t += 0.125) {
			SectionType type = typeAt(sections, t);
			Sample sample = sampler.sample(t, type);
			energyFrames.add(new EnergyFrame(t, sample.energy()));
			bands.add(new FrequencyBands(t, sample.low(), sample.mid(), sample.high()));
		}

		// Percussive: 16th-note dense beats; others: quarter notes with section-scaled strength
		double beatStep = style == PERCUSSIVE ? beatDur / 4.0 : beatDur;
		for (double t = 0; t <= duration + 1e-9; t += beatStep) {
			SectionType type = typeAt(sections, t);
			Sample sample = sampler.sample(t, type);
			beats.add(new DetectedBeat(t, sample.beatStrength()));
		}

		BeatGrid grid = new BeatGrid(bpm, duration);
		AudioFeatureTimeline timeline = new AudioFeatureTimeline(
			duration,
			beats,
			energyFrames,
			bands,
			new WaveformExtractor.WaveformFrame[0],
			bpm,
			grid
		);
		List<Double> beatTimes = beats.stream().map(DetectedBeat::getTimeSeconds).toList();
		List<BarGridBuilder.BarSpan> bars = BarGridBuilder.build(grid, duration);
		MusicStructure structure = new MusicStructure(duration, beatTimes, bars, phrases, sections);
		return new Bundle(style, timeline, structure);
	}

	private static List<MusicStructure.PhraseSpan> phrasesFor(
		List<StructuralSection> sections,
		double defaultRepetition
	) {
		List<MusicStructure.PhraseSpan> phrases = new ArrayList<>();
		int index = 0;
		for (StructuralSection section : sections) {
			double mid = (section.getStartSeconds() + section.getEndSeconds()) * 0.5;
			phrases.add(new MusicStructure.PhraseSpan(
				section.getStartSeconds(),
				mid,
				index++,
				defaultRepetition
			));
			phrases.add(new MusicStructure.PhraseSpan(
				mid,
				section.getEndSeconds(),
				index++,
				Math.min(1.0, defaultRepetition + 0.35)
			));
		}
		return phrases;
	}

	private static StructuralSection section(double start, double end, SectionType type) {
		return new StructuralSection(start, end, type, type.name().toLowerCase());
	}

	private static SectionType typeAt(List<StructuralSection> sections, double timeSeconds) {
		for (int i = 0; i < sections.size(); i++) {
			StructuralSection section = sections.get(i);
			boolean withinEnd = i == sections.size() - 1
				? timeSeconds <= section.getEndSeconds()
				: timeSeconds < section.getEndSeconds();
			if (timeSeconds >= section.getStartSeconds() && withinEnd) {
				return section.getType();
			}
		}
		return SectionType.VERSE;
	}
}
