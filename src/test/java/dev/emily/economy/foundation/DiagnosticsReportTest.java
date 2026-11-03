package dev.emily.economy.foundation;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsReportTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID CORRELATION = UUID.randomUUID();
    private static final SchedulingMetrics METRICS = new SchedulingMetrics(
            new SchedulingMetrics.Queue(256, 0, 0, 6_000_000_000L),
            new SchedulingMetrics.Queue(64, 1, 1_000_000L),
            new SchedulingMetrics.Queue(128, 0, 0),
            new StorageLifecycle.Progress("projection:private-name", 3, 9, false),
            new StorageLifecycle.EconomicProgress(5, 2, false, false), true, 4);

    @Test void playersSeeOnlySafeAvailabilityAndProgressForBothCommands() {
        for (boolean detailed : new boolean[]{false, true}) {
            assertEquals(List.of("Economy is starting (projection rebuild)."),
                    DiagnosticsReport.render(false, detailed, ServerServices.State.INITIALIZING, METRICS,
                            new DiagnosticsReport.StorageDetails(WORLD, 1, 12, "healthy"),
                            "STORAGE_IDENTITY", CORRELATION, 2.5));
        }
    }

    @Test void operatorsSeeDetachedMetricsAndOnlyControlledText() {
        String report = String.join("\n", DiagnosticsReport.render(true, true, ServerServices.State.STOPPED,
                METRICS, new DiagnosticsReport.StorageDetails(WORLD, 1, 12, "/private-health"),
                "/private-failure", CORRELATION, null));
        assertTrue(report.contains("Storage unknown, world " + WORLD + ", schema 1, last committed sequence 12"));
        assertTrue(report.contains("projection rebuild, checkpoint 3, target 9"));
        assertTrue(report.contains("frontier 5, backlog 2, observed rate unavailable"));
        assertTrue(report.contains("oldest active 6000 ms, possible stall"));
        assertTrue(report.contains("Unresolved at shutdown 4"));
        assertTrue(report.contains("Last failure unknown, correlation " + CORRELATION));
        assertFalse(report.contains("private"));
        assertFalse(report.contains("ETA"));
    }

    @Test void operatorStatusShowsMeasuredRateAndControlledFailure() {
        String status = String.join("\n", DiagnosticsReport.render(true, false, ServerServices.State.READY,
                METRICS, null, "STORAGE_IDENTITY", CORRELATION, 2.5));
        assertTrue(status.contains("observed rate 2.50 intervals/s"));
        assertTrue(status.contains("Last failure STORAGE_IDENTITY"));
    }
}
