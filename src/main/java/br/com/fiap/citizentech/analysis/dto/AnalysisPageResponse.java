package br.com.fiap.citizentech.analysis.dto;
import org.springframework.data.domain.Page;
import java.util.List;
public record AnalysisPageResponse(List<AnalysisResponse> content,int page,int size,long totalElements,int totalPages) {
    public static AnalysisPageResponse from(Page<AnalysisResponse> p) {
        return new AnalysisPageResponse(p.getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages());
    }
}
