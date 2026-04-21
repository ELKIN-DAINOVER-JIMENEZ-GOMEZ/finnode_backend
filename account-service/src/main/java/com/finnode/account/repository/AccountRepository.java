package com.finnode.account.repository;

import com.finnode.account.model.Account;
import com.finnode.account.model.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio JPA para la entidad Account.
 *
 * Extiende JpaRepository para obtener gratuitamente:
 *   - save(), findById(), findAll(), deleteById(), count(), existsById()
 *   - Paginación y ordenamiento con Pageable
 *
 * Los métodos personalizados aquí cubren los casos de acceso específicos
 * del account-service: buscar por userId, por accountNumber y por transactionId.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    // -------------------------------------------------------------------------
    // Búsquedas simples (lectura)
    // -------------------------------------------------------------------------

    /**
     * Busca la cuenta bancaria asociada a un usuario.
     * Usado por AccountEventConsumer al recibir UserRegisteredEvent
     * y por AccountController para validar propiedad de la cuenta.
     *
     * @param userId UUID del usuario (proveniente del JWT o del evento Kafka)
     * @return Optional con la cuenta si existe
     */
    Optional<Account> findByUserId(UUID userId);

    /**
     * Busca una cuenta por su número de cuenta visible al usuario.
     * Usado en transferencias donde el origen especifica el número de cuenta destino.
     *
     * @param accountNumber número de cuenta (ej: "FN-0000000001")
     * @return Optional con la cuenta si existe
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Verifica si ya existe una cuenta para un userId dado.
     * Usado en AccountService.createAccount() para evitar cuentas duplicadas
     * ante reenvíos del evento UserRegisteredEvent (idempotencia Kafka).
     *
     * @param userId UUID del usuario
     * @return true si ya tiene cuenta bancaria
     */
    boolean existsByUserId(UUID userId);

    /**
     * Verifica si un número de cuenta ya está en uso.
     * Usado durante la generación del accountNumber para garantizar unicidad.
     *
     * @param accountNumber número de cuenta a verificar
     * @return true si ya existe
     */
    boolean existsByAccountNumber(String accountNumber);

    // -------------------------------------------------------------------------
    // Búsqueda con Optimistic Locking explícito (escritura concurrente)
    // -------------------------------------------------------------------------

    /**
     * Busca una cuenta por ID con bloqueo optimista explícito.
     *
     * Usar este método en operaciones de escritura concurrente (reservar fondos,
     * confirmar débito, acreditar) garantiza que Hibernate valide la versión
     * antes de ejecutar el UPDATE, lanzando OptimisticLockException si colisiona.
     *
     * Flujo típico en AccountService:
     *   1. findByIdWithOptimisticLock(accountId)  ← obtiene entidad + versión
     *   2. account.reserveFunds(amount)            ← modifica estado en memoria
     *   3. save(account)                           ← Hibernate valida @Version
     *
     * @param id UUID de la cuenta
     * @return Optional con la cuenta bloqueada
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithOptimisticLock(@Param("id") UUID id);

    // -------------------------------------------------------------------------
    // Búsquedas por estado
    // -------------------------------------------------------------------------

    /**
     * Cuenta cuántas cuentas activas existen para un usuario.
     * En la versión actual cada usuario tiene exactamente una cuenta,
     * pero este método soporta futuras extensiones multi-cuenta.
     *
     * @param userId UUID del usuario
     * @param status estado a filtrar
     * @return cantidad de cuentas con ese estado
     */
    long countByUserIdAndStatus(UUID userId, AccountStatus status);
}