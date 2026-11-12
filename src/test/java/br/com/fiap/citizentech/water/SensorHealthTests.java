package br.com.fiap.citizentech.water;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SensorHealthTests {
    Instant now=Instant.parse("2026-11-11T12:00:00Z");
    String health(List<SensorHealthCalculator.Sample> rows) {
        return SensorHealthCalculator.calculate(rows,now,Duration.ofHours(24),Duration.ofHours(48));
    }
    @Test void timestampsAndBoundaries() {
        assertEquals("INSUFFICIENT_DATA",health(List.of()));
        assertEquals("INSUFFICIENT_DATA",health(List.of(new SensorHealthCalculator.Sample(now.plusSeconds(1),20.0,null,null))));
        assertEquals("STALE",health(List.of(new SensorHealthCalculator.Sample(now.minusSeconds(86400),20.0,null,null))));
        assertEquals("OFFLINE",health(List.of(new SensorHealthCalculator.Sample(now.minusSeconds(172800),20.0,null,null))));
        assertEquals("HEALTHY",health(List.of(new SensorHealthCalculator.Sample(now,20.0,null,null))));
    }
    @Test void flatAndJumpAreOnlyPossibleFaults() {
        var flat=java.util.stream.IntStream.range(0,6).mapToObj(i->new SensorHealthCalculator.Sample(now.minusSeconds(i*600),28.0,25.0,65.0)).toList();
        assertEquals("POSSIBLE_FAULT",health(flat));
        assertEquals("POSSIBLE_FAULT",health(List.of(new SensorHealthCalculator.Sample(now,90.0,null,null),
                new SensorHealthCalculator.Sample(now.minusSeconds(60),20.0,null,null))));
        assertEquals(health(flat),health(flat));
    }
}
