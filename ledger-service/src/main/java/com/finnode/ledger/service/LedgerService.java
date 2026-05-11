package com.finnode.ledger.service;

import com.finnode.ledger.dto.LedgerEntryResponse;
import com.finnode.ledger.dto.LedgerHistoryResponse;
import com.finnode.ledger.event.LedgerEntriesRecordedEvent;
import com.finnode.ledger.event.PaymentCompletedEvent;
import com.finnode.ledger.event.PaymentReversedEvent;
import com.finnode.ledger.exception.LedgerEntryNotFoundException;
import com.finnode.ledger.exception.LedgerImbalanceException;
import com.finnode.ledger.kafka.LedgerEventPublisher;
import com.finnode.ledger.model.EntryType;
import com.finnode.ledger.model.LedgerEntry;
import com.finnode.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Servicio principal del ledger-service.
 *
 * Orquesta toda la lógica de negocio contable:
 *   - Registrar asientos DEBIT + CREDIT al confirmar un pago
 *   - Registrar asientos REVERSAL al revertir un pago fallido (Saga)
 *   - Consultar el historial contable de una cuenta
 *
 * PRINCIPIO CLAVE: cada operación de escritura es @Transactional.
 * Los dos asientos (DEBIT + CREDIT) se persisten juntos o no se persiste
 * ninguno. Esto garantiza la Contabilidad de Partida Doble a nivel de base
 * de datos — es imposible que exista un DEBIT sin su CREDIT correspondiente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerEventPublisher   ledgerEventPublisher;

    // -------------------------------------------------------------------------
    // OPERACIONES DE ESCRITURA (disparadas por eventos Kafka)
    // -------------------------------------------------------------------------

    /**
     * Registra el par de asientos contables (DEBIT + CREDIT) para una
     * transferencia exitosa.
     *
     * Llamado por LedgerEventConsumer al recibir [payment.completed].
     *
     * Flujo interno:
     *   1. Idempotencia: verifica si la transacción ya fue registrada
     *   2. Validaciones: monto positivo, moneda no nula
     *   3. Crea asiento DEBIT  en la cuenta origen
     *   4. Crea asiento CREDIT en la cuenta destino
     *   5. Valida que ambos montos sean iguales (Partida Doble)
     *   6. Persiste ambos en una sola @Transactional
     *   7. Publica LedgerEntriesRecordedEvent para notificar al payment-orchestrator
     *
     * @param event evento recibido desde Kafka [payment.completed]
     * @throws LedgerImbalanceException si los montos del DEBIT y CREDIT no cuadran
     */
    @Transactional
    public void recordEntries(PaymentCompletedEvent event) {

        // 1. IDEMPOTENCIA
        // Si Kafka reentrega este evento (ya fue procesado antes), lo ignoramos.
        // Verificamos si ya existe algún asiento con este transactionId.
        if (ledgerEntryRepository.existsByTransactionId(event.transactionId())) {
            log.warn("[LEDGER] Transacción ya registrada, evento ignorado | transactionId={}",
                    event.transactionId());
            return;
        }

        // 2. VALIDACIONES DE NEGOCIO
        validateAmount(event.amount(), event.transactionId());
        validateCurrency(event.currency(), event.transactionId());

        // 3. CREAR ASIENTO DEBIT (salida de fondos de la cuenta origen)
        LedgerEntry debitEntry = LedgerEntry.builder()
                .transactionId(event.transactionId())
                .accountId(event.sourceAccountId())
                .counterpartAccountId(event.destinationAccountId())
                .entryType(EntryType.DEBIT)
                .amount(event.amount())
                .currency(event.currency())
                .description(buildDebitDescription(event.destinationAccountId()))
                .build();

        // 4. CREAR ASIENTO CREDIT (entrada de fondos a la cuenta destino)
        LedgerEntry creditEntry = LedgerEntry.builder()
                .transactionId(event.transactionId())
                .accountId(event.destinationAccountId())
                .counterpartAccountId(event.sourceAccountId())
                .entryType(EntryType.CREDIT)
                .amount(event.amount())
                .currency(event.currency())
                .description(buildCreditDescription(event.sourceAccountId()))
                .build();

        // 5. VALIDAR PARTIDA DOBLE
        // En este caso siempre cuadran porque ambos usan event.amount(),
        // pero la validación existe para proteger contra errores futuros
        // en que los montos pudieran divergir por lógica de conversión.
        validateBalance(debitEntry.getAmount(), creditEntry.getAmount(), event.transactionId());

        // 6. PERSISTIR AMBOS ASIENTOS EN UNA SOLA TRANSACCIÓN
        // Si falla el save, Spring revierte automáticamente y Kafka reentrega.
        LedgerEntry savedDebit  = ledgerEntryRepository.save(debitEntry);
        LedgerEntry savedCredit = ledgerEntryRepository.save(creditEntry);

        log.info("[LEDGER] Asientos registrados | transactionId={} | debitId={} | creditId={}",
                event.transactionId(), savedDebit.getId(), savedCredit.getId());

        // 7. PUBLICAR CONFIRMACIÓN AL PAYMENT-ORCHESTRATOR
        LedgerEntriesRecordedEvent confirmation = new LedgerEntriesRecordedEvent(
                event.transactionId(),
                savedDebit.getId(),
                savedCredit.getId(),
                event.amount(),
                event.currency(),
                false,           // reversalConfirmed = false → es un pago exitoso
                Instant.now()
        );

        ledgerEventPublisher.publishLedgerEntriesRecorded(confirmation);
    }

    /**
     * Registra los asientos de compensación (REVERSAL_DEBIT + REVERSAL_CREDIT)
     * cuando el Patrón Saga ordena revertir una transferencia fallida.
     *
     * Llamado por LedgerEventConsumer al recibir [payment.reversed].
     *
     * Los asientos originales NUNCA se eliminan. Se crean nuevos asientos
     * que los neutralizan matemáticamente, preservando la trazabilidad completa.
     *
     * Flujo interno:
     *   1. Idempotencia: verifica si la reversión ya fue registrada
     *   2. Validaciones: monto positivo, moneda no nula
     *   3. Crea asiento REVERSAL_CREDIT en la cuenta origen (devuelve fondos)
     *   4. Crea asiento REVERSAL_DEBIT  en la cuenta destino (retira fondos)
     *   5. Persiste ambos en una sola @Transactional
     *   6. Publica LedgerEntriesRecordedEvent con reversalConfirmed = true
     *
     * @param event evento recibido desde Kafka [payment.reversed]
     */
    @Transactional
    public void recordReversalEntries(PaymentReversedEvent event) {

        // 1. IDEMPOTENCIA PARA REVERSIONES
        // Verificamos si ya existen asientos de reversión para esta transacción.
        // Un asiento de reversión tiene el mismo transactionId que el original
        // pero con entryType REVERSAL_DEBIT o REVERSAL_CREDIT.
        boolean reversalAlreadyRecorded = ledgerEntryRepository
                .findByTransactionId(event.transactionId())
                .stream()
                .anyMatch(entry -> entry.getEntryType().isReversal());

        if (reversalAlreadyRecorded) {
            log.warn("[LEDGER] Reversión ya registrada, evento ignorado | transactionId={}",
                    event.transactionId());
            return;
        }

        // 2. VALIDACIONES DE NEGOCIO
        validateAmount(event.amount(), event.transactionId());
        validateCurrency(event.currency(), event.transactionId());

        String reversalDescription = buildReversalDescription(event.reversalReason());

        // 3. CREAR ASIENTO REVERSAL_CREDIT (devuelve fondos a la cuenta origen)
        // La cuenta origen había recibido un DEBIT → este REVERSAL_CREDIT lo cancela
        LedgerEntry reversalCreditEntry = LedgerEntry.builder()
                .transactionId(event.transactionId())
                .accountId(event.sourceAccountId())
                .counterpartAccountId(event.destinationAccountId())
                .entryType(EntryType.REVERSAL_CREDIT)
                .amount(event.amount())
                .currency(event.currency())
                .description(reversalDescription)
                .build();

        // 4. CREAR ASIENTO REVERSAL_DEBIT (retira fondos de la cuenta destino)
        // La cuenta destino había recibido un CREDIT → este REVERSAL_DEBIT lo cancela
        LedgerEntry reversalDebitEntry = LedgerEntry.builder()
                .transactionId(event.transactionId())
                .accountId(event.destinationAccountId())
                .counterpartAccountId(event.sourceAccountId())
                .entryType(EntryType.REVERSAL_DEBIT)
                .amount(event.amount())
                .currency(event.currency())
                .description(reversalDescription)
                .build();

        // 5. PERSISTIR AMBOS ASIENTOS DE COMPENSACIÓN EN UNA SOLA TRANSACCIÓN
        LedgerEntry savedReversalCredit = ledgerEntryRepository.save(reversalCreditEntry);
        LedgerEntry savedReversalDebit  = ledgerEntryRepository.save(reversalDebitEntry);

        log.info("[LEDGER] Asientos de reversión registrados | transactionId={} | reason={} | reversalCreditId={} | reversalDebitId={}",
                event.transactionId(), event.reversalReason(),
                savedReversalCredit.getId(), savedReversalDebit.getId());

        // 6. PUBLICAR CONFIRMACIÓN DE REVERSIÓN AL PAYMENT-ORCHESTRATOR
        LedgerEntriesRecordedEvent confirmation = new LedgerEntriesRecordedEvent(
                event.transactionId(),
                null,            // debitEntryId = null en reversiones
                null,            // creditEntryId = null en reversiones
                event.amount(),
                event.currency(),
                true,            // reversalConfirmed = true → es una reversión Saga
                Instant.now()
        );

        ledgerEventPublisher.publishLedgerEntriesRecorded(confirmation);
    }

    // -------------------------------------------------------------------------
    // OPERACIONES DE LECTURA (CQRS — Query Side)
    // -------------------------------------------------------------------------

    /**
     * Retorna el historial contable completo de una cuenta.
     * Incluye totales calculados del período para el frontend.
     *
     * Llamado por LedgerController en:
     *   GET /ledger/{accountId}/entries
     *
     * @param accountId ID de la cuenta a consultar
     * @return historial con lista de asientos y totales calculados
     * @throws LedgerEntryNotFoundException si la cuenta no tiene asientos registrados
     */
    @Transactional(readOnly = true)
    public LedgerHistoryResponse getHistory(UUID accountId) {

        if (!ledgerEntryRepository.existsByAccountId(accountId)) {
            throw new LedgerEntryNotFoundException(accountId);
        }

        List<LedgerEntry> entries = ledgerEntryRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId);

        return buildHistoryResponse(accountId, entries, null, null);
    }

    /**
     * Retorna el historial contable de una cuenta filtrado por rango de fechas.
     *
     * Llamado por LedgerController en:
     *   GET /ledger/{accountId}/entries?from=2025-01-01T00:00:00Z&to=2025-01-31T23:59:59Z
     *
     * @param accountId ID de la cuenta
     * @param from      inicio del período (inclusive)
     * @param to        fin del período (inclusive)
     * @return historial filtrado con totales del período
     * @throws LedgerEntryNotFoundException si la cuenta no tiene asientos en el período
     */
    @Transactional(readOnly = true)
    public LedgerHistoryResponse getHistoryByDateRange(UUID accountId, Instant from, Instant to) {

        if (!ledgerEntryRepository.existsByAccountId(accountId)) {
            throw new LedgerEntryNotFoundException(accountId);
        }

        List<LedgerEntry> entries = ledgerEntryRepository
                .findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(accountId, from, to);

        return buildHistoryResponse(accountId, entries, from, to);
    }

    /**
     * Retorna todos los asientos contables asociados a una transacción específica.
     *
     * En una transferencia exitosa retorna 2 asientos (DEBIT + CREDIT).
     * En una transacción revertida retorna 4 asientos
     * (DEBIT + CREDIT + REVERSAL_DEBIT + REVERSAL_CREDIT).
     *
     * Llamado por LedgerController en:
     *   GET /ledger/transactions/{transactionId}
     *
     * @param transactionId ID de la transacción a consultar
     * @return lista de asientos de la transacción
     */
    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getEntriesByTransactionId(UUID transactionId) {

        List<LedgerEntry> entries = ledgerEntryRepository
                .findByTransactionId(transactionId);

        log.info("[LEDGER] Consulta de asientos por transacción | transactionId={} | totalEntries={}",
                transactionId, entries.size());

        return entries.stream()
                .map(LedgerEntryResponse::fromEntity)
                .toList();
    }

    // -------------------------------------------------------------------------
    // MÉTODOS PRIVADOS DE APOYO
    // -------------------------------------------------------------------------

    /**
     * Construye el LedgerHistoryResponse calculando los totales
     * directamente desde la lista de asientos en memoria.
     * Evita queries adicionales a la base de datos para los totales.
     */
    private LedgerHistoryResponse buildHistoryResponse(
            UUID accountId,
            List<LedgerEntry> entries,
            Instant from,
            Instant to
    ) {
        List<LedgerEntryResponse> entryResponses = entries.stream()
                .map(LedgerEntryResponse::fromEntity)
                .toList();

        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getEntryType().decrementsBalance())
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getEntryType().incrementsBalance())
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant periodFrom = (from != null) ? from : entries.isEmpty()
                                                     ? Instant.now()
                                                     : entries.getLast().getCreatedAt();

        Instant periodTo = (to != null) ? to : entries.isEmpty()
                                               ? Instant.now()
                                               : entries.getFirst().getCreatedAt();

        return LedgerHistoryResponse.builder()
                .accountId(accountId)
                .entries(entryResponses)
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .netBalance(totalCredits.subtract(totalDebits))
                .periodFrom(periodFrom)
                .periodTo(periodTo)
                .totalEntries(entries.size())
                .build();
    }

    /**
     * Valida que el monto sea positivo y no nulo.
     * Un monto de cero o negativo indica un error en el evento del orquestador.
     */
    private void validateAmount(BigDecimal amount, UUID transactionId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new LedgerImbalanceException(
                    "Monto inválido en la transacción: " + transactionId + " | amount=" + amount
            );
        }
    }

    /**
     * Valida que el código de moneda no sea nulo ni vacío.
     */
    private void validateCurrency(String currency, UUID transactionId) {
        if (currency == null || currency.isBlank()) {
            throw new LedgerImbalanceException(
                    "Moneda inválida en la transacción: " + transactionId
            );
        }
    }

    /**
     * Valida que el monto del asiento DEBIT sea igual al del CREDIT.
     * Es la invariante central de la Contabilidad de Partida Doble.
     */
    private void validateBalance(BigDecimal debitAmount, BigDecimal creditAmount, UUID transactionId) {
        if (debitAmount.compareTo(creditAmount) != 0) {
            throw new LedgerImbalanceException(
                    "Desbalance contable en transacción: " + transactionId
                            + " | debit=" + debitAmount + " | credit=" + creditAmount
            );
        }
    }

    private String buildDebitDescription(UUID destinationAccountId) {
        return "Transferencia enviada a cuenta " + destinationAccountId.toString().substring(0, 8).toUpperCase();
    }

    private String buildCreditDescription(UUID sourceAccountId) {
        return "Transferencia recibida de cuenta " + sourceAccountId.toString().substring(0, 8).toUpperCase();
    }

    private String buildReversalDescription(String reversalReason) {
        return "Reversión automática Saga — razón: " + reversalReason;
    }
}