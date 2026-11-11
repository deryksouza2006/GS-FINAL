package br.com.fiap.citizentech.water;

import java.time.*;
import java.util.*;

/** Demo thresholds are explicit inputs, never agronomic prescriptions. */
public final class WaterRiskCalculator {
    public enum Level { NORMAL, ATTENTION, CRITICAL, INSUFFICIENT_DATA }
    public record Thresholds(double criticalAvailability, double attentionAvailability,
            double highVpd, double rapidDryingPerHour, Duration staleAfter, String version) {
        public Thresholds {
            if(!Double.isFinite(criticalAvailability) || !Double.isFinite(attentionAvailability)
                    || criticalAvailability<0 || attentionAvailability>100 || criticalAvailability>=attentionAvailability
                    || !Double.isFinite(highVpd) || highVpd<=0 || !Double.isFinite(rapidDryingPerHour)
                    || rapidDryingPerHour>=0 || staleAfter==null || staleAfter.isZero() || staleAfter.isNegative()
                    || version==null || version.isBlank()) throw new IllegalArgumentException("Limiares inválidos");
        }
    }
    public record Measurement(long id,long sensorId,Instant measuredAt,Double soilMoisture,
                              Double airTemperature,Double airHumidity,boolean simulated) { }
    public record Result(Level riskLevel,Double waterAvailabilityPercent,Double rawAvailabilityPercent,
                         Double vpdKpa,Double moistureChangePerHour,List<String> rulesTriggered,
                         List<String> limitations,String rulesVersion,boolean demo) {
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        public String getDataQuality() {
            if (riskLevel == Level.INSUFFICIENT_DATA) return "INSUFFICIENT";
            return limitations.isEmpty() ? "COMPLETE" : "PARTIAL";
        }
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        public String getMoistureTrend() {
            if (moistureChangePerHour == null) return "INSUFFICIENT_DATA";
            if (rulesTriggered.contains("RAPID_SOIL_DRYING")) return "DRYING_FAST";
            if (moistureChangePerHour < 0) return "DRYING_SLOWLY";
            return moistureChangePerHour > 0 ? "RECOVERING" : "STABLE";
        }
    }

    public Result calculate(List<Measurement> input,Double fieldCapacity,Double wiltingPoint,
                            Instant reference,Thresholds rules,boolean demoProfile) {
        Objects.requireNonNull(reference); Objects.requireNonNull(rules);
        var measurements=input.stream().filter(m->m.measuredAt()!=null && !m.measuredAt().isAfter(reference))
                .sorted(Comparator.comparing(Measurement::measuredAt).thenComparingLong(Measurement::id).reversed()).toList();
        var limitations=new ArrayList<String>(); var triggered=new ArrayList<String>();
        var fresh=measurements.stream().filter(m->!m.measuredAt().isBefore(reference.minus(rules.staleAfter()))).toList();
        boolean demo=demoProfile || input.stream().anyMatch(Measurement::simulated);
        Measurement soil=fresh.stream().filter(m->m.soilMoisture()!=null).findFirst().orElse(null);
        Measurement atmosphere=fresh.stream().filter(m->m.airTemperature()!=null && m.airHumidity()!=null).findFirst().orElse(null);
        Measurement temperature = fresh.stream().filter(m -> m.airTemperature()!=null).findFirst().orElse(null);
        Measurement humidity = fresh.stream().filter(m -> m.airHumidity()!=null).findFirst().orElse(null);
        Double availability=null,raw=null,vpd=null,trend=null;
        if (soil == null) {
            limitations.add("Umidade do solo recente ausente");
            triggered.add(measurements.stream().anyMatch(m -> m.soilMoisture() != null)
                    ? "STALE_SENSOR_DATA" : "MISSING_REQUIRED_SENSOR");
        }
        if(fieldCapacity==null || wiltingPoint==null) { limitations.add("Perfil agronômico ausente"); triggered.add("INVALID_AGRONOMIC_PROFILE"); }
        if(soil!=null && fieldCapacity!=null && wiltingPoint!=null) {
            var a=WaterIndicators.availability(soil.soilMoisture(),fieldCapacity,wiltingPoint);
            availability=a.displayPercent(); raw=a.rawPercent();
            var previous=measurements.stream().filter(m->m.sensorId()==soil.sensorId() && m.soilMoisture()!=null
                    && Duration.between(m.measuredAt(),soil.measuredAt()).compareTo(Duration.ofMinutes(5))>=0
                    && !m.measuredAt().isBefore(reference.minus(rules.staleAfter()))).findFirst();
            if(previous.isPresent()) trend=WaterIndicators.moistureChangePerHour(previous.get().soilMoisture(),previous.get().measuredAt(),
                    soil.soilMoisture(),soil.measuredAt(),Duration.ofMinutes(5));
            else limitations.add("Histórico insuficiente para tendência");
        }
        if (atmosphere!=null) {
            vpd=WaterIndicators.vpdKpa(atmosphere.airTemperature(),atmosphere.airHumidity());
        } else if (temperature!=null && humidity!=null
                && Duration.between(temperature.measuredAt(), humidity.measuredAt()).abs().compareTo(Duration.ofMinutes(5))<=0) {
            vpd=WaterIndicators.vpdKpa(temperature.airTemperature(),humidity.airHumidity());
        } else {
            limitations.add("Temperatura e umidade do ar simultâneas ausentes");
        }
        Level level=availability==null ? Level.INSUFFICIENT_DATA : Level.NORMAL;
        if (raw != null && raw > 100) {
            triggered.add("EXCESS_SOIL_WATER");
            limitations.add("Umidade acima da capacidade de campo. Verifique a medição e a drenagem.");
            level = Level.ATTENTION;
        }
        if(availability!=null && availability<=rules.attentionAvailability()) {
            triggered.add("LOW_WATER_AVAILABILITY");
            level=availability<=rules.criticalAvailability() ? Level.CRITICAL : Level.ATTENTION;
        }
        if(vpd!=null && vpd>=rules.highVpd()) { triggered.add("HIGH_ATMOSPHERIC_DEMAND"); if(level==Level.NORMAL) level=Level.ATTENTION; }
        if(trend!=null && trend<=rules.rapidDryingPerHour()) { triggered.add("RAPID_SOIL_DRYING"); if(level==Level.NORMAL) level=Level.ATTENTION; }
        return new Result(level,availability,raw,vpd,trend,List.copyOf(triggered),List.copyOf(limitations),rules.version(),demo);
    }
}
