package br.com.fiap.citizentech.farm.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record FarmPageResponse(List<FarmResponse> content, int page, int size,
                               long totalElements, int totalPages) {
    public static FarmPageResponse from(Page<FarmResponse> farms) {
        return new FarmPageResponse(farms.getContent(), farms.getNumber(), farms.getSize(),
                farms.getTotalElements(), farms.getTotalPages());
    }
}
