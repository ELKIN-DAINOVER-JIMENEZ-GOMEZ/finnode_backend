package com.finnode.account.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.finnode.account.event.UserRegisteredEvent;
import com.finnode.account.service.AccountService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountEventConsumer Unit Tests")
class AccountEventConsumerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private Acknowledgment acknowledgment;

    private AccountEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AccountEventConsumer(accountService);
    }

    //este tes de onUserRegister es para verificar que el consumer delega correctamente al service y hace el ack cuando todo va bien, y que no hace ack si el service lanza una excepción. Lo mismo para onPaymentReverse, verificando que delega a releaseFunds y hace ack, o no hace ack si releaseFunds falla.
    @Test
    @DisplayName("onUserRegistered delega en service y hace ack")
    void onUserRegisteredSuccess() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(UUID.randomUUID())
                .email("new@finnode.com")
                .fullName("New User")
                .timestamp(Instant.now())
                .build();

        consumer.onUserRegistered(event, 10L, acknowledgment);

        verify(accountService).createAccount(event);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onUserRegistered sin ack cuando service falla")
    void onUserRegisteredFailure() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(UUID.randomUUID())
                .email("new@finnode.com")
                .fullName("New User")
                .timestamp(Instant.now())
                .build();

        doThrow(new RuntimeException("db error")).when(accountService).createAccount(event);

        assertThatThrownBy(() -> consumer.onUserRegistered(event, 11L, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db error");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("onPaymentReverse delega releaseFunds y hace ack")
    void onPaymentReverseSuccess() {
        AccountEventConsumer.ReleaseReserveMessage event = new AccountEventConsumer.ReleaseReserveMessage(
                "txn-20",
                UUID.randomUUID(),
                new BigDecimal("15.00"),
                Instant.now()
        );

        consumer.onPaymentReverse(event, 12L, acknowledgment);

        verify(accountService).releaseFunds(event.getAccountId(), event.getTransactionId(), event.getAmount());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onPaymentReverse sin ack cuando service falla")
    void onPaymentReverseFailure() {
        AccountEventConsumer.ReleaseReserveMessage event = new AccountEventConsumer.ReleaseReserveMessage(
                "txn-21",
                UUID.randomUUID(),
                new BigDecimal("18.00"),
                Instant.now()
        );

        doThrow(new RuntimeException("release error"))
                .when(accountService)
                .releaseFunds(event.getAccountId(), event.getTransactionId(), event.getAmount());

        assertThatThrownBy(() -> consumer.onPaymentReverse(event, 13L, acknowledgment))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("release error");

        verify(acknowledgment, never()).acknowledge();
    }
}

