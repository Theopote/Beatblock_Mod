package com.beatblock.audio.process;

import com.beatblock.audio.AnalysisSummary;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnalyzerProcessIoTest {

	@Test
	void consumeStdoutParsesProgressResultAndError() throws IOException {
		String stdout = """
			PROGRESS BPM_DETECTION 42
			RESULT {"bpm":128.0,"beat_count":10}
			ERROR something went wrong
			""";
		List<String> steps = new ArrayList<>();
		List<Integer> percents = new ArrayList<>();

		String raw = AnalyzerProcessIo.consumeStdout(
			new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8)),
			(step, pct) -> {
				steps.add(step);
				percents.add(pct);
			}
		);

		AnalyzerProcessIo.StdoutParseResult parsed = AnalyzerProcessIo.parseStdoutResult(raw);
		assertEquals("{\"bpm\":128.0,\"beat_count\":10}", parsed.resultJson());
		assertEquals("something went wrong\n", parsed.errorText());
		assertEquals(List.of("BPM_DETECTION"), steps);
		assertEquals(List.of(42), percents);
	}

	@Test
	void parseResultSummaryReadsKnownFields() {
		AnalysisSummary summary = AnalyzerProcessIo.parseResultSummary(
			"{\"bpm\":120.5,\"beat_count\":64,\"section_count\":4,\"duration_ms\":180000,"
				+ "\"separation_mode\":\"demucs\",\"cache_source\":\"fresh\"}"
		);
		assertNotNull(summary);
		assertEquals(120.5f, summary.bpm());
		assertEquals(64, summary.beatCount());
		assertEquals(4, summary.sectionCount());
		assertEquals(180_000L, summary.durationMs());
		assertEquals("demucs", summary.separationMode());
		assertEquals("fresh", summary.cacheSource());
	}

	@Test
	void parseResultSummaryReturnsNullForBlankInput() {
		assertNull(AnalyzerProcessIo.parseResultSummary(""));
		assertNull(AnalyzerProcessIo.parseResultSummary(null));
	}
}
