package br.com.fiap.citizentech.water;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class WaterRiskCalculatorTests {
    final Instant now=Instant.parse("2026-11-11T12:00:00Z");
    final WaterRiskCalculator calculator=new WaterRiskCalculator();
    final WaterRiskCalculator.Thresholds rules=new WaterRiskCalculator.Thresholds(20,40,2,-3,Duration.ofHours(24),"demo-v1");
    @Test void deterministicRiskAndInclusiveBoundary() {
        var readings=List.of(new WaterRiskCalculator.Measurement(1,1,now,16.0,30.0,50.0,true));
        var a=calculator.calculate(readings,32.0,12.0,now,rules,true);
        assertEquals(WaterRiskCalculator.Level.CRITICAL,a.riskLevel());
        assertEquals(a,calculator.calculate(readings,32.0,12.0,now,rules,true));
        assertTrue(a.demo());
    }
    @Test void missingProfileNeverProducesNormal() {
        var readings=List.of(new WaterRiskCalculator.Measurement(1,1,now,24.0,25.0,70.0,false));
        assertEquals(WaterRiskCalculator.Level.INSUFFICIENT_DATA,
                calculator.calculate(readings,null,null,now,rules,false).riskLevel());
    }
    @Test void staleReadingCannotProduceAvailability() {
        var readings=List.of(new WaterRiskCalculator.Measurement(1,1,now.minus(Duration.ofDays(2)),24.0,null,null,false));
        assertNull(calculator.calculate(readings,32.0,12.0,now,rules,false).waterAvailabilityPercent());
    }
    @Test void trendNeverMixesSensors() {
        var readings=List.of(new WaterRiskCalculator.Measurement(1,1,now,24.0,null,null,false),
                new WaterRiskCalculator.Measurement(2,2,now.minusSeconds(3600),35.0,null,null,false));
        assertNull(calculator.calculate(readings,32.0,12.0,now,rules,false).moistureChangePerHour());
    }
}
