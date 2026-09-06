package com.beatblock.client.export;

import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.GlobalEventPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalVisualEffectFrameCompositorTest {
    @Test
    void screenTintIsCompositedAtExportResolutionWithoutDependingOnImGui() {
        byte[] frame = opaqueBlackFrame(3, 2);
        var event = new CompiledGlobalEvent("tint", 0,
            new GlobalEventPayload.ScreenTint("", 1, 1, 0, 0, 0));
        GlobalVisualEffectFrameCompositor.composite(frame, 3, 2, List.of(event), 12);
        for (int i = 0; i < frame.length; i += 4) {
            assertEquals(89, Byte.toUnsignedInt(frame[i]));
            assertEquals(0, Byte.toUnsignedInt(frame[i + 1]));
            assertEquals(0, Byte.toUnsignedInt(frame[i + 2]));
            assertEquals(255, Byte.toUnsignedInt(frame[i + 3]));
        }
    }

    @Test
    void screenFlashUsesTimelineTimeAndFadesDeterministically() {
        var event = new CompiledGlobalEvent("flash", 10,
            new GlobalEventPayload.ScreenFlash("", 1, 0, 0, 2));
        byte[] start = opaqueBlackFrame(1, 1);
        byte[] middle = opaqueBlackFrame(1, 1);
        byte[] end = opaqueBlackFrame(1, 1);
        GlobalVisualEffectFrameCompositor.composite(start, 1, 1, List.of(event), 10);
        GlobalVisualEffectFrameCompositor.composite(middle, 1, 1, List.of(event), 11);
        GlobalVisualEffectFrameCompositor.composite(end, 1, 1, List.of(event), 12);
        assertEquals(217, Byte.toUnsignedInt(start[0]));
        assertEquals(108, Byte.toUnsignedInt(middle[0]));
        assertEquals(0, Byte.toUnsignedInt(end[0]));
    }

    @Test
    void exportVfxStateOverloadMatchesEventsResolve() {
        var event = new CompiledGlobalEvent("tint", 0,
            new GlobalEventPayload.ScreenTint("", 1, 1, 0, 0, 0));
        byte[] fromEvents = opaqueBlackFrame(1, 1);
        byte[] fromState = opaqueBlackFrame(1, 1);
        GlobalVisualEffectFrameCompositor.composite(fromEvents, 1, 1, List.of(event), 5);
        GlobalVisualEffectFrameCompositor.composite(
            fromState, 1, 1, ExportVfxState.resolve(List.of(event), 5), 5);
        assertArrayEquals(fromEvents, fromState);
    }

    @Test
    void environmentLightingIsNotMisrepresentedAsScreenOverlay() {
        byte[] frame = opaqueBlackFrame(2, 3);
        byte[] original = frame.clone();
        var event = new CompiledGlobalEvent("light", 0,
            new GlobalEventPayload.EnvironmentLighting("", 1, 1, 1, 1, 0));
        GlobalVisualEffectFrameCompositor.composite(frame, 2, 3, List.of(event), 1);
        assertArrayEquals(original, frame);
    }

    @Test
    void laterExpiredTintClearsEarlierPersistentTint() {
        byte[] frame = opaqueBlackFrame(1, 1);
        var persistent = new CompiledGlobalEvent("red", 0,
            new GlobalEventPayload.ScreenTint("", 1, 1, 0, 0, 0));
        var temporary = new CompiledGlobalEvent("blue", 2,
            new GlobalEventPayload.ScreenTint("", 1, 0, 0, 1, 1));
        GlobalVisualEffectFrameCompositor.composite(frame, 1, 1, List.of(persistent, temporary), 4);
        assertArrayEquals(opaqueBlackFrame(1, 1), frame);
    }

    private static byte[] opaqueBlackFrame(int width, int height) {
        byte[] frame = new byte[width * height * 4];
        for (int i = 3; i < frame.length; i += 4) frame[i] = (byte) 255;
        return frame;
    }
}
