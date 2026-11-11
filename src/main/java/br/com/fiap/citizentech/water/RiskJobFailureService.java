package br.com.fiap.citizentech.water;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class RiskJobFailureService {
    private final RiskJobRepository jobs;
    @Transactional public void fail(Long id) { jobs.lock(id).ifPresent(RiskJob::fail); }
}
