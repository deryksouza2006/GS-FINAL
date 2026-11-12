package br.com.fiap.citizentech.water;

import java.time.*;
import java.util.*;

/** Demonstration heuristics, not a hardware diagnosis. */
public final class SensorHealthCalculator {
    public record Sample(Instant time, Double soil, Double temperature, Double humidity) { }
    public static String calculate(List<Sample> samples, Instant now, Duration stale, Duration offline) {
        var rows=samples.stream().filter(s->s.time()!=null && !s.time().isAfter(now))
                .sorted(Comparator.comparing(Sample::time).reversed()).toList();
        if(rows.isEmpty()) return "INSUFFICIENT_DATA";
        var latest=rows.getFirst(); var age=Duration.between(latest.time(),now);
        if(age.compareTo(offline)>=0) return "OFFLINE";
        if(age.compareTo(stale)>=0) return "STALE";
        var fresh=rows.stream().filter(s->Duration.between(s.time(),now).compareTo(stale)<0).toList();
        if(fresh.size()>=6 && Duration.between(fresh.get(5).time(),latest.time()).toMinutes()>=50
                && (latest.soil()!=null || latest.temperature()!=null || latest.humidity()!=null)
                && fresh.subList(0,6).stream().allMatch(s->Objects.equals(s.soil(),latest.soil())
                    && Objects.equals(s.temperature(),latest.temperature()) && Objects.equals(s.humidity(),latest.humidity())))
            return "POSSIBLE_FAULT";
        if(fresh.size()>=2) {
            var previous=fresh.get(1); long seconds=Duration.between(previous.time(),latest.time()).getSeconds();
            if(seconds>0 && seconds<=300 && (jump(previous.soil(),latest.soil(),40)
                    || jump(previous.temperature(),latest.temperature(),20) || jump(previous.humidity(),latest.humidity(),50)))
                return "POSSIBLE_FAULT";
        }
        return "HEALTHY";
    }
    private static boolean jump(Double a,Double b,double limit) { return a!=null && b!=null && Math.abs(a-b)>limit; }
}
