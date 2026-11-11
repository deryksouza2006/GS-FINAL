package br.com.fiap.citizentech.water;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class RainfallCalculatorTests {
    @Test void sumsContiguousIntervalsWithoutDoubleCounting() {
        Instant t=Instant.parse("2026-11-11T00:00:00Z");
        var rows=List.of(new RainfallCalculator.Interval(1,t,t.plusSeconds(3600),3),
                new RainfallCalculator.Interval(1,t.plusSeconds(3600),t.plusSeconds(7200),2));
        var result=RainfallCalculator.accumulate(rows,1,t,t.plusSeconds(7200));
        assertEquals(5,result.millimeters()); assertTrue(result.complete());
        var duplicate=List.of(rows.getFirst(),rows.getFirst());
        assertNull(RainfallCalculator.accumulate(duplicate,1,t,t.plusSeconds(7200)).millimeters());
        assertNull(RainfallCalculator.accumulate(List.of(),1,t,t.plusSeconds(7200)).millimeters());
    }
}
