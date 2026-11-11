package br.com.fiap.citizentech.water;

import java.time.Duration;
import java.time.Instant;

/** Pure calculations. Percentages must use the same calibrated volumetric basis. */
public final class WaterIndicators {
    private WaterIndicators() { }

    public record Availability(double rawPercent, double displayPercent) { }

    public static Availability availability(double moisture, double fieldCapacity, double wiltingPoint) {
        percent(moisture);
        percent(fieldCapacity);
        percent(wiltingPoint);
        if (fieldCapacity <= wiltingPoint) {
            throw new IllegalArgumentException("Capacidade de campo deve superar o ponto de murcha");
        }
        double raw = (moisture - wiltingPoint) / (fieldCapacity - wiltingPoint) * 100;
        // Above field capacity does not, by itself, establish soil saturation.
        return new Availability(raw, Math.max(0, Math.min(100, raw)));
    }

    public static double vpdKpa(double airTemperatureCelsius, double relativeHumidityPercent) {
        percent(relativeHumidityPercent);
        if (!Double.isFinite(airTemperatureCelsius) || airTemperatureCelsius < -50 || airTemperatureCelsius > 70) {
            throw new IllegalArgumentException("Temperatura fora da faixa suportada");
        }
        double saturationPressure = 0.6108 * Math.exp(17.27 * airTemperatureCelsius / (airTemperatureCelsius + 237.3));
        return saturationPressure * (1 - relativeHumidityPercent / 100);
    }

    public static double moistureChangePerHour(double previous, Instant previousAt,
                                              double current, Instant currentAt, Duration minimumInterval) {
        percent(previous);
        percent(current);
        if (previousAt == null || currentAt == null || minimumInterval == null
                || minimumInterval.isNegative() || minimumInterval.isZero()) {
            throw new IllegalArgumentException("Datas e intervalo mínimo positivo são obrigatórios");
        }
        Duration elapsed = Duration.between(previousAt, currentAt);
        if (elapsed.isNegative() || elapsed.isZero() || elapsed.compareTo(minimumInterval) < 0) {
            throw new IllegalArgumentException("Intervalo insuficiente entre leituras");
        }
        return (current - previous) / (elapsed.toSeconds() / 3600.0 + elapsed.getNano() / 3_600_000_000_000.0);
    }

    private static void percent(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 100) {
            throw new IllegalArgumentException("Percentual deve estar entre 0 e 100");
        }
    }
}
