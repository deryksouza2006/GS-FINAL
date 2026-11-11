package br.com.fiap.citizentech.water;
import java.time.Instant;
import java.util.*;
public final class RainfallCalculator {
    public record Interval(long sensorId,Instant start,Instant end,double millimeters) { }
    public record Total(Double millimeters,boolean complete,String limitation) { }
    public static Total accumulate(List<Interval> input,long sensorId,Instant from,Instant to) {
        if(!from.isBefore(to)) throw new IllegalArgumentException("Janela inválida");
        var rows=input.stream().filter(r->r.sensorId()==sensorId && r.end().isAfter(from) && r.start().isBefore(to))
                .sorted(Comparator.comparing(Interval::start)).toList();
        if(rows.isEmpty()) return new Total(null,false,"Sem medições de chuva");
        Instant cursor=from; double total=0; boolean complete=true;
        for(var row:rows) {
            if(!Double.isFinite(row.millimeters()) || row.millimeters()<0 || !row.start().isBefore(row.end()))
                throw new IllegalArgumentException("Intervalo de chuva inválido");
            if(row.start().isBefore(cursor) || row.end().isAfter(to))
                return new Total(null,false,"Intervalos sobrepostos ou atravessando a janela; acumulado não estimado");
            if(row.start().isAfter(cursor)) complete=false;
            total+=row.millimeters(); cursor=row.end();
        }
        if(!cursor.equals(to)) complete=false;
        return new Total(total,complete,complete ? null : "Cobertura parcial da janela");
    }
}
