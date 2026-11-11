package br.com.fiap.citizentech.activity;
import java.time.Instant;
import java.math.BigDecimal;
public record ActivityResponse(Long id, Long cropAreaId, Long authorId, FieldActivity.Type type,
        Instant performedAt, String description, BigDecimal quantity, String unit, String source,
        Instant createdAt, Instant updatedAt) {
    static ActivityResponse from(FieldActivity a) {
        return new ActivityResponse(a.getId(),a.getCropArea().getId(),a.getAuthor().getId(),a.getType(),
                a.getPerformedAt(),a.getDescription(),a.getQuantity(),a.getUnit(),"MANUAL",a.getCreatedAt(),a.getUpdatedAt());
    }
}
