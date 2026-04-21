package com.finnode.account.kafka;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finnode.account.event.FundsReleasedEvent;
import com.finnode.account.event.FundsReservationFailedEvent;
import com.finnode.account.event.FundsReservedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountEventPublisher Unit Tests")
class AccountEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AccountEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AccountEventPublisher(kafkaTemplate);
    }

    @Test
    @DisplayName("publishFundsReserved envía al topic account.funds-reserved")
    void publishFundsReservedSendsToCorrectTopic() {
        FundsReservedEvent event = FundsReservedEvent.builder()
                .transactionId("txn-r1")
                .accountId(UUID.randomUUID())
                .amount(new BigDecimal("10.00"))
                .timestamp(Instant.now())
                .build();

        when(kafkaTemplate.send("account.funds-reserved", "txn-r1", event))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        publisher.publishFundsReserved(event);

        verify(kafkaTemplate).send("account.funds-reserved", "txn-r1", event);
    }

    @Test
    @DisplayName("publishReservationFailed envía al topic account.funds-reservation-failed")
    void publishReservationFailedSendsToCorrectTopic() {
        FundsReservationFailedEvent event = FundsReservationFailedEvent.builder()
                .transactionId("txn-f1")
                .accountId(UUID.randomUUID())
                .reason("INSUFFICIENT_FUNDS")
                .timestamp(Instant.now())
                .build();

        when(kafkaTemplate.send("account.funds-reservation-failed", "txn-f1", event))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        publisher.publishReservationFailed(event);

        verify(kafkaTemplate).send("account.funds-reservation-failed", "txn-f1", event);
    }

    @Test
    @DisplayName("publishFundsReleased envía al topic account.funds-released")
    void publishFundsReleasedSendsToCorrectTopic() {
        FundsReleasedEvent event = FundsReleasedEvent.builder()
                .transactionId("txn-l1")
                .accountId(UUID.randomUUID())
                .amount(new BigDecimal("2.00"))
                .timestamp(Instant.now())
                .build();

        when(kafkaTemplate.send("account.funds-released", "txn-l1", event))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        publisher.publishFundsReleased(event);

        verify(kafkaTemplate).send("account.funds-released", "txn-l1", event);
    }
}


