package com.beatblock.client;

import com.beatblock.engine.BlockControlExecutor;
import com.beatblock.engine.ScrubPreviewOverlay;
import com.beatblock.engine.WorldMutationSink;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.playback.CompiledStageEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Dispatches compiled stage actions (ANIMATE / BUILD / PLACE / CLEAR) onto the animation engine.
 * Keeps mutation capture / action reporting callbacks on the host ({@link BeatBlockClientDriver}).
 */
public final class StageEventDispatcher {

	public interface Host {
		BeatBlockContext ctx();

		@Nullable CompiledStageEvent resolveCompiled(TimelineAnimationEvent event);

		void recordActionReport(
			TimelineAnimationEvent event,
			int mutationCount,
			String status,
			String detail
		);

		void captureTimelineMutationOriginalState(World world, BlockPos pos, BlockState currentState);
	}

	private final Host host;
	private final BooleanSupplier exportIsolated;

	public StageEventDispatcher(Host host, BooleanSupplier exportIsolated) {
		this.host = host;
		this.exportIsolated = exportIsolated != null ? exportIsolated : () -> false;
	}

	public void apply(
		TimelineAnimationEvent event,
		@Nullable CompiledStageEvent compiledHint,
		boolean previewOnly,
		double[] referenceBeats,
		double bpm
	) {
		var engine = host.ctx().blockAnimationEngine();
		if (event == null || engine == null) return;
		if (!event.getPayload().passesEnergyGate()) {
			host.recordActionReport(event, 0, "SKIPPED", "energy-below-threshold");
			return;
		}

		TimelineAnimationActionMode actionMode = event.getActionMode();
		if (actionMode == TimelineAnimationActionMode.ANIMATE) {
			var compiled = compiledHint != null ? compiledHint : host.resolveCompiled(event);
			if (!previewOnly && compiled != null) {
				engine.scheduleTimelineEvent(compiled, referenceBeats, bpm);
			} else {
				engine.scheduleTimelineEvent(event, referenceBeats, bpm);
			}
			host.recordActionReport(event, 0, "ANIMATE", "scheduled");
			return;
		}

		if (actionMode == TimelineAnimationActionMode.BUILD) {
			var inst = engine.getBuildSequencer().schedule(event, referenceBeats, bpm);
			if (inst != null) {
				String detail = previewOnly
					? "preview-scheduled-" + inst.getTotalBlocks() + "-blocks"
					: "scheduled-" + inst.getTotalBlocks() + "-blocks";
				host.recordActionReport(event, inst.getTotalBlocks(), "BUILD", detail);
			} else {
				host.recordActionReport(event, 0, "SKIPPED", "build-no-target");
			}
			return;
		}

		MinecraftClient mc = MinecraftClient.getInstance();
		World world = mc != null ? mc.world : null;
		if (world == null) {
			host.recordActionReport(event, 0, "SKIPPED", "no-world");
			return;
		}
		var plan = engine.planControl(event, world);
		var mutations = plan.mutations();
		if (mutations.isEmpty()) {
			String detail = plan.skipReason() != null
				? "skip-" + plan.skipReason().name().toLowerCase(Locale.ROOT)
				: "skip-no-change";
			host.recordActionReport(event, 0, "SKIPPED", detail);
			return;
		}
		if (previewOnly) {
			ScrubPreviewOverlay.applyMutations(engine.getAnimationPlayer(), mutations);
			host.recordActionReport(event, mutations.size(), "PREVIEW", "overlay");
			return;
		}
		for (BlockControlExecutor.BlockMutation mutation : mutations) {
			host.captureTimelineMutationOriginalState(world, mutation.pos(), mutation.fromState());
		}
		WorldMutationSink sink = exportIsolated.getAsBoolean()
			? BeatBlockAuthoritativeWorldMutator.awaitingSinkFor(engine.getBlockControlExecutor(), world)
			: BeatBlockAuthoritativeWorldMutator.sinkFor(engine.getBlockControlExecutor(), world);
		engine.applyControlMutations(mutations, sink);
		host.recordActionReport(event, mutations.size(), "APPLIED", "ok");
	}
}
