package com.finnode.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finnode.account.event.FundsReleasedEvent;
import com.finnode.account.event.FundsReservationFailedEvent;
import com.finnode.account.event.FundsReservedEvent;
import com.finnode.account.event.UserRegisteredEvent;
import com.finnode.account.exception.AccountNotFoundException;
import com.finnode.account.exception.AccountSuspendedException;
import com.finnode.account.exception.InsufficientFundsException;
import com.finnode.account.kafka.AccountEventPublisher;
import com.finnode.account.model.Account;
import com.finnode.account.model.AccountStatus;
import com.finnode.account.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountEventPublisher accountEventPublisher;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, accountEventPublisher);
    }

    @Test
    @DisplayName("createAccount guarda cuenta nueva cuando no existe")
    void createAccountSuccess() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(userId)
                .email("new@finnode.com")
                .fullName("New User")
                .timestamp(Instant.now())
                .build();

        when(accountRepository.existsByUserId(userId)).thenReturn(false);

        accountService.createAccount(event);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getBalance()).isEqualByComparingTo("0");
        assertThat(saved.getReservedBalance()).isEqualByComparingTo("0");
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getAccountNumber()).startsWith("FN");
    }

    @Test
    @DisplayName("createAccount no guarda si el usuario ya tiene cuenta")
    void createAccountIdempotent() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = UserRegisteredEvent.builder().userId(userId).build();

        when(accountRepository.existsByUserId(userId)).thenReturn(true);

        accountService.createAccount(event);

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("reserveFunds incrementa reservedBalance y publica FundsReservedEvent")
    void reserveFundsSuccess() {
        UUID accountId = UUID.randomUUID();
        String transactionId = "txn-10";
        Account account = Account.builder()
                .id(accountId)
                .balance(new BigDecimal("100.00"))
                .reservedBalance(new BigDecimal("20.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        accountService.reserveFunds(accountId, transactionId, new BigDecimal("30.00"));

        assertThat(account.getReservedBalance()).isEqualByComparingTo("50.00");
        verify(accountRepository).save(account);

        ArgumentCaptor<FundsReservedEvent> eventCaptor = ArgumentCaptor.forClass(FundsReservedEvent.class);
        verify(accountEventPublisher).publishFundsReserved(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTransactionId()).isEqualTo(transactionId);
        assertThat(eventCaptor.getValue().getAccountId()).isEqualTo(accountId);
        assertThat(eventCaptor.getValue().getAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("reserveFunds con saldo insuficiente publica failure y lanza excepción")
    void reserveFundsInsufficient() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .balance(new BigDecimal("25.00"))
                .reservedBalance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.reserveFunds(accountId, "txn-11", new BigDecimal("50.00")))
                .isInstanceOf(InsufficientFundsException.class);

        verify(accountRepository, never()).save(any(Account.class));
        ArgumentCaptor<FundsReservationFailedEvent> eventCaptor = ArgumentCaptor.forClass(FundsReservationFailedEvent.class);
        verify(accountEventPublisher).publishReservationFailed(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("reserveFunds con cuenta suspendida lanza AccountSuspendedException")
    void reserveFundsSuspendedAccount() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .balance(new BigDecimal("100.00"))
                .reservedBalance(BigDecimal.ZERO)
                .status(AccountStatus.SUSPENDED)
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.reserveFunds(accountId, "txn-12", new BigDecimal("10.00")))
                .isInstanceOf(AccountSuspendedException.class);

        verify(accountRepository, never()).save(any(Account.class));
        verify(accountEventPublisher, never()).publishFundsReserved(any(FundsReservedEvent.class));
        verify(accountEventPublisher, never()).publishReservationFailed(any(FundsReservationFailedEvent.class));
    }

    @Test
    @DisplayName("reserveFunds con cuenta inexistente lanza AccountNotFoundException")
    void reserveFundsAccountNotFound() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.reserveFunds(accountId, "txn-13", new BigDecimal("10.00")))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountEventPublisher, never()).publishFundsReserved(any(FundsReservedEvent.class));
    }

    @Test
    @DisplayName("releaseFunds reduce reservedBalance y publica FundsReleasedEvent")
    void releaseFundsSuccess() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .balance(new BigDecimal("100.00"))
                .reservedBalance(new BigDecimal("40.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        accountService.releaseFunds(accountId, "txn-14", new BigDecimal("15.00"));

        assertThat(account.getReservedBalance()).isEqualByComparingTo("25.00");
        verify(accountRepository).save(account);

        ArgumentCaptor<FundsReleasedEvent> eventCaptor = ArgumentCaptor.forClass(FundsReleasedEvent.class);
        verify(accountEventPublisher).publishFundsReleased(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getTransactionId()).isEqualTo("txn-14");
    }

    @Test
    @DisplayName("releaseFunds nunca deja reservedBalance en negativo")
    void releaseFundsFloorAtZero() {
        UUID accountId = UUID.randomUUID();
        Account account = Account.builder()
                .id(accountId)
                .balance(new BigDecimal("100.00"))
                .reservedBalance(new BigDecimal("5.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        accountService.releaseFunds(accountId, "txn-15", new BigDecimal("8.00"));

        assertThat(account.getReservedBalance()).isEqualByComparingTo("0.00");
    }
}

