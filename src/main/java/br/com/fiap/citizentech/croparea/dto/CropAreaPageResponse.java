package br.com.fiap.citizentech.croparea.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record CropAreaPageResponse(List<CropAreaResponse> content, int page, int size,
                                   long totalElements, int totalPages) {
    public static CropAreaPageResponse from(Page<CropAreaResponse> areas) {
        return new CropAreaPageResponse(areas.getContent(), areas.getNumber(), areas.getSize(),
                areas.getTotalElements(), areas.getTotalPages());
    }
}
