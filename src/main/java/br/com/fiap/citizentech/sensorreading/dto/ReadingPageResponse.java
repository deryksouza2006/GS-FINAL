package br.com.fiap.citizentech.sensorreading.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record ReadingPageResponse(List<ReadingResponse> content, int page, int size, long totalElements, int totalPages) {
    public static ReadingPageResponse from(Page<ReadingResponse> readings) {
        return new ReadingPageResponse(readings.getContent(), readings.getNumber(), readings.getSize(),
                readings.getTotalElements(), readings.getTotalPages());
    }
}
