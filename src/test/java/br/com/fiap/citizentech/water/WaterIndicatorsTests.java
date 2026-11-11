package br.com.fiap.citizentech.water;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class WaterIndicatorsTests {
    @Test void availabilityUsesCalibratedSoilProfile() {
        assertEquals(60, WaterIndicators.availability(24, 32, 12).rawPercent(), 0.0001);
        assertEquals(100, WaterIndicators.availability(40, 32, 12).displayPercent());
        assertEquals(140, WaterIndicators.availability(40, 32, 12).rawPercent());
        assertEquals(0, WaterIndicators.availability(5, 32, 12).displayPercent());
    }
    @Test void rejectsInvalidProfilesAndNonFiniteMeasurements() {
        assertThrows(IllegalArgumentException.class, () -> WaterIndicators.availability(24, 12, 12));
        assertThrows(IllegalArgumentException.class, () -> WaterIndicators.availability(Double.NaN, 32, 12));
        assertThrows(IllegalArgumentException.class, () -> WaterIndicators.vpdKpa(30, 101));
    }
    @Test void vpdUsesAirTemperatureAndRelativeHumidity() {
        assertEquals(0, WaterIndicators.vpdKpa(30, 100));
        assertEquals(2.1215, WaterIndicators.vpdKpa(30, 50), 0.001);
    }
    @Test void trendUsesElapsedTimeAndRejectsDuplicateTimestamps() {
        Instant start = Instant.parse("2026-11-11T10:00:00Z");
        assertEquals(-2, WaterIndicators.moistureChangePerHour(30, start, 26,
                start.plusSeconds(7200), Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class, () -> WaterIndicators.moistureChangePerHour(
                30, start, 26, start, Duration.ofMinutes(5)));
    }
}
