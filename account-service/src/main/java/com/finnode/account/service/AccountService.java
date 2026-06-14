package com.finnode.account.service;

import com.finnode.account.dto.*;
import com.finnode.account.event.*;
import com.finnode.account.exception.*;
import com.finnode.account.kafka.AccountEventPublisher;
import com.finnode.account.model.Account;
import com.finnode.account.model.AccountStatus;
import com.finnode.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountEventPublisher accountEventPublisher;  // ← inyectado aquí, no en el Consumer

    // -------------------------------------------------------------------------
    // Crear cuenta (disparado por UserRegisteredEvent desde Kafka)
    // -------------------------------------------------------------------------

    @Transactional
    public void createAccount(UserRegisteredEvent event) {
        if (accountRepository.existsByUserId(event.getUserId())) {
            log.warn("Cuenta ya existe para userId={}", event.getUserId());
            return;
        }

        Account account = Account.builder()
                .id(UUID.randomUUID())
                .userId(event.getUserId())
                .accountNumber(generateAccountNumber())
                .balance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .build();

        accountRepository.save(account);
        log.info("Cuenta creada | userId={} | accountNumber={}", event.getUserId(), account.getAccountNumber());
    }

    // -------------------------------------------------------------------------
    // Consultar saldo (CQRS Query)
    // -------------------------------------------------------------------------

    public BalanceResponse getBalance(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return BalanceResponse.from(account);
    }

    // -------------------------------------------------------------------------
    // Consultar detalle completo de la cuenta (CQRS Query)
    // -------------------------------------------------------------------------

    public AccountResponse getAccountDetails(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return AccountResponse.from(account);
    }

    // -------------------------------------------------------------------------
    // Reservar fondos (CQRS Command — llamado por el Controller REST)
    // -------------------------------------------------------------------------

    @Transactional
    public void reserveFunds(UUID accountId, String transactionId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        validateActive(account);

        BigDecimal available = account.getBalance().subtract(account.getReservedBalance());
        if (available.compareTo(amount) < 0) {
            accountEventPublisher.publishReservationFailed(
                    FundsReservationFailedEvent.builder()
                            .transactionId(transactionId)
                            .accountId(accountId)
                            .reason("INSUFFICIENT_FUNDS")
                            .timestamp(Instant.now())
                            .build()
            );
            throw new InsufficientFundsException(accountId, available, amount);
        }

        account.setReservedBalance(account.getReservedBalance().add(amount));
        accountRepository.save(account);

        // Publica la señal verde para que el payment-orchestrator continúe el Saga
        accountEventPublisher.publishFundsReserved(
                FundsReservedEvent.builder()
                        .transactionId(transactionId)
                        .accountId(accountId)
                        .amount(amount)
                        .timestamp(Instant.now())
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // Confirmar débito definitivo (CQRS Command)
    // -------------------------------------------------------------------------

    @Transactional
    public void confirmDebit(UUID accountId, String transactionId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        validateActive(account);

        // Descuenta el saldo real
        BigDecimal newBalance = account.getBalance().subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            newBalance = BigDecimal.ZERO;
        }
        account.setBalance(newBalance);

        // Libera la reserva
        BigDecimal newReserved = account.getReservedBalance().subtract(amount);
        if (newReserved.compareTo(BigDecimal.ZERO) < 0) {
            newReserved = BigDecimal.ZERO;
        }
        account.setReservedBalance(newReserved);

        accountRepository.save(account);
        log.info("Débito confirmado | transactionId={} | accountId={} | amount={}", transactionId, accountId, amount);
    }

    // -------------------------------------------------------------------------
    // Acreditar fondos (CQRS Command)
    // -------------------------------------------------------------------------

    @Transactional
    public void creditFunds(UUID accountId, String transactionId, BigDecimal amount, UUID sourceAccountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        validateActive(account);

        // Incrementa el saldo disponible (sin afectar reservedBalance)
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.info("Fondos acreditados | transactionId={} | destinationAccountId={} | sourceAccountId={} | amount={}",
                transactionId, accountId, sourceAccountId, amount);
    }

    // -------------------------------------------------------------------------
    // Liberar fondos (Compensación Saga — llamado por el Consumer)
    // -------------------------------------------------------------------------

    @Transactional
    public void releaseFunds(UUID accountId, String transactionId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal newReserved = account.getReservedBalance().subtract(amount);
        account.setReservedBalance(newReserved.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newReserved);
        accountRepository.save(account);

        log.info("Fondos liberados | transactionId={} | accountId={} | amount={}", transactionId, accountId, amount);

        // El servicio publica el evento directamente — el Consumer no necesita saber nada de esto
        accountEventPublisher.publishFundsReleased(
                FundsReleasedEvent.builder()
                        .transactionId(transactionId)
                        .accountId(accountId)
                        .amount(amount)
                        .timestamp(Instant.now())
                        .build()
        );
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    private void validateActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountSuspendedException(account.getId());
        }
    }

    private String generateAccountNumber() {
        return "FN" + System.currentTimeMillis();
    }
}