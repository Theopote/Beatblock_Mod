package com.beatblock.automap.choreography;

import com.beatblock.audio.analysis.structure.MusicStructure;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.timeline.Timeline;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 在 beatmap 导入时写入结构层 {@link ChoreographyPlan}（段落 + 小节），
 * 保留用户已生成的动作/镜头短语。
 */
public final class ChoreographyPlanSeeder {

	private ChoreographyPlanSeeder() {}

	public static void seedFromBeatmap(Timeline timeline, Beatmap beatmap) {
		if (timeline == null || beatmap == null) return;

		MusicStructure structure = BeatmapStructureAdapter.fromBeatmap(beatmap);
		AutoMapConfig config = ChoreographyPlanStore.loadConfig(timeline);
		if (config == null) {
			config = AutoMapConfig.createDefault();
		}

		ChoreographyPlan structurePlan = ChoreographyPlanBuilder.fromMusicStructure(
			structure,
			List.of(),
			List.of(),
			List.of(),
			AutoMapStyle.EDM,
			config
		);

		ChoreographyPlan existing = ChoreographyPlanStore.loadPlan(timeline);
		ChoreographyPlan merged = mergeStructure(existing, structurePlan);
		ChoreographyPlanStore.save(timeline, merged, config);
	}

	static ChoreographyPlan mergeStructure(@Nullable ChoreographyPlan existing, ChoreographyPlan structurePlan) {
		return ChoreographyStructureMerger.mergeStructureOnly(existing, structurePlan);
	}
}
