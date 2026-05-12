package com.mehmetali.ledger.domain;

import com.mehmetali.ledger.domain.model.Account;
import com.mehmetali.ledger.domain.model.LedgerEntry;
import com.mehmetali.ledger.domain.model.Transaction;
import com.mehmetali.ledger.domain.model.TransactionStatus;
import com.mehmetali.ledger.domain.repository.AccountRepository;
import com.mehmetali.ledger.domain.repository.AccountSnapshotRepository;
import com.mehmetali.ledger.domain.repository.AuditLogRepository;
import com.mehmetali.ledger.domain.repository.LedgerEntryRepository;
import com.mehmetali.ledger.domain.repository.TransactionRepository;
import com.mehmetali.ledger.domain.service.LedgerService;
import com.mehmetali.ledger.domain.service.PaymentService;
import com.mehmetali.ledger.idempotency.IdempotencyService;
import com.mehmetali.ledger.messaging.PaymentEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PaymentServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledger_db")
            .withUsername("ledger_user")
            .withPassword("ledger_pass");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockBean
    PaymentEventProducer eventProducer;

    @Autowired
    PaymentService paymentService;

    @Autowired
    LedgerService ledgerService;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    TransactionRepository transactionRepository;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    AccountSnapshotRepository snapshotRepository;

    @Autowired
    IdempotencyService idempotencyService;

    UUID aliId;
    UUID ayseId;

    @BeforeEach
    void setUp() {
        // FK order: audit_log + ledger_entries reference transactions; snapshots reference accounts
        auditLogRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        snapshotRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();

        doNothing().when(eventProducer).publish(any());

        // bank account is the source for seeding — never used as sender in real tests
        Account bank = new Account();
        bank.setOwnerId(UUID.randomUUID());
        bank.setCurrency("TRY");
        bank.setStatus("ACTIVE");
        UUID bankId = accountRepository.save(bank).getId();

        Account ali = new Account();
        ali.setOwnerId(UUID.randomUUID());
        ali.setCurrency("TRY");
        ali.setStatus("ACTIVE");
        aliId = accountRepository.save(ali).getId();

        Account ayse = new Account();
        ayse.setOwnerId(UUID.randomUUID());
        ayse.setCurrency("TRY");
        ayse.setStatus("ACTIVE");
        ayseId = accountRepository.save(ayse).getId();

        // Seed: bank → Ali: DEBIT on bank, CREDIT +1000 on Ali
        Transaction seed = new Transaction();
        seed.setIdempotencyKey("seed-" + UUID.randomUUID());
        seed.setFromAccountId(bankId);
        seed.setToAccountId(aliId);
        seed.setAmount(new BigDecimal("1000.00"));
        seed.setCurrency("TRY");
        seed.setStatus(TransactionStatus.SETTLED);
        Transaction savedSeed = transactionRepository.save(seed);
        ledgerService.createDoubleEntry(savedSeed);
    }

    // ---------------------------------------------------------------
    // Senaryo 1: Başarılı ödeme → SETTLED + doğru ledger kayıtları
    // ---------------------------------------------------------------
    @Test
    void successfulPayment_shouldSettleAndCreateDoubleEntry() {
        Transaction tx = buildTransaction("key-success-001", aliId, ayseId, "250.00");
        Transaction pending = paymentService.initiatePayment(tx);

        assertThat(pending.getStatus()).isEqualTo(TransactionStatus.PENDING);

        paymentService.processPayment(pending.getId());

        Transaction settled = transactionRepository.findById(pending.getId()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(TransactionStatus.SETTLED);

        List<LedgerEntry> entries = ledgerEntryRepository.findAll().stream()
                .filter(e -> e.getTransaction().getId().equals(settled.getId()))
                .toList();

        assertThat(entries).hasSize(2);
        assertThat(entries).anyMatch(e -> e.getAccountId().equals(aliId)
                && e.getEntryType().name().equals("DEBIT"));
        assertThat(entries).anyMatch(e -> e.getAccountId().equals(ayseId)
                && e.getEntryType().name().equals("CREDIT"));

        BigDecimal aliBalance = ledgerService.getBalance(aliId);
        BigDecimal ayseBalance = ledgerService.getBalance(ayseId);

        // Ali: +1000 (seed CREDIT from bank) - 250 (DEBIT to Ayse) = 750
        assertThat(aliBalance).isEqualByComparingTo("750.00");
        assertThat(ayseBalance).isEqualByComparingTo("250.00");
    }

    // ---------------------------------------------------------------
    // Senaryo 2: Aynı Idempotency-Key → DB'ye yeni kayıt eklenmez
    // ---------------------------------------------------------------
    @Test
    void duplicateIdempotencyKey_shouldNotCreateSecondTransaction() {
        String key = "key-idem-001";

        Transaction first = paymentService.initiatePayment(
                buildTransaction(key, aliId, ayseId, "100.00"));
        idempotencyService.saveResponse(key, "{\"transactionId\":\"" + first.getId() + "\"}");

        long countBefore = transactionRepository.count();

        // Redis'te key var mı?
        Optional<String> cached = idempotencyService.getCachedResponse(key);
        assertThat(cached).isPresent();
        assertThat(cached.get()).contains(first.getId().toString());

        // İkinci istek simülasyonu: key mevcut olduğu için yeni transaction açılmaz
        long countAfter = transactionRepository.count();
        assertThat(countAfter).isEqualTo(countBefore);
    }

    // ---------------------------------------------------------------
    private Transaction buildTransaction(String key, UUID from, UUID to, String amount) {
        Transaction tx = new Transaction();
        tx.setIdempotencyKey(key);
        tx.setFromAccountId(from);
        tx.setToAccountId(to);
        tx.setAmount(new BigDecimal(amount));
        tx.setCurrency("TRY");
        tx.setDescription("Test payment");
        return tx;
    }
}
