package com.finnode.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.finnode.account.dto.ReleaseReserveRequest;
import com.finnode.account.dto.ReserveFundsRequest;
import com.finnode.account.exception.InsufficientFundsException;
import com.finnode.account.service.AccountService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountController Unit Tests")
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    private AccountController accountController;

    @BeforeEach
    void setUp() {
        accountController = new AccountController(accountService);
    }

    @Test
    @DisplayName("POST /accounts/{id}/reserve retorna 202 y delega al servicio")
    void reserveFundsSuccess() {
        UUID accountId = UUID.randomUUID();
        ReserveFundsRequest request = new ReserveFundsRequest(new BigDecimal("50.00"), "txn-1");

        var response = accountController.reserveFunds(accountId, request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(accountService).reserveFunds(accountId, "txn-1", new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("POST /accounts/{id}/release retorna 202 y delega al servicio")
    void releaseFundsSuccess() {
        UUID accountId = UUID.randomUUID();
        ReleaseReserveRequest request = new ReleaseReserveRequest("txn-2", new BigDecimal("20.00"));

        var response = accountController.releaseFunds(accountId, request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(accountService).releaseFunds(accountId, "txn-2", new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("Errores del servicio se propagan desde reserve")
    void reserveFundsPropagatesException() {
        UUID accountId = UUID.randomUUID();
        ReserveFundsRequest request = new ReserveFundsRequest(new BigDecimal("120.00"), "txn-3");

        doThrow(new InsufficientFundsException(accountId, new BigDecimal("10.00"), new BigDecimal("120.00")))
                .when(accountService)
                .reserveFunds(accountId, "txn-3", new BigDecimal("120.00"));

        assertThatThrownBy(() -> accountController.reserveFunds(accountId, request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Fondos insuficientes");
    }
}

