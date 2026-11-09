package br.com.fiap.citizentech.shared.outbox;

public interface OutboxEventSender {
    void send(OutboxEvent event);
}
