package com.mehmetali.ledger.domain.service;

import com.mehmetali.ledger.api.dto.BalanceResponse;
import com.mehmetali.ledger.domain.model.Account;
import com.mehmetali.ledger.domain.repository.AccountRepository;
import com.mehmetali.ledger.domain.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private static final String KEY_PREFIX = "account:balance:";

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final StringRedisTemplate redisTemplate;

    public BalanceResponse getBalance(UUID accountId) {
        String key = KEY_PREFIX + accountId;
        String cached = redisTemplate.opsForValue().get(key);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        if (cached != null) {
            return new BalanceResponse(
                    accountId,
                    new BigDecimal(cached),
                    account.getCurrency(),
                    "CACHE",
                    LocalDateTime.now()
            );
        }

        BigDecimal balance = ledgerEntryRepository.calculateBalance(accountId);
        redisTemplate.opsForValue().set(key, balance.toPlainString());

        return new BalanceResponse(accountId, balance, account.getCurrency(), "DB", LocalDateTime.now());
    }

    public void updateCache(UUID accountId) {
        BigDecimal balance = ledgerEntryRepository.calculateBalance(accountId);
        redisTemplate.opsForValue().set(KEY_PREFIX + accountId, balance.toPlainString());
    }

    public void invalidateCache(UUID accountId) {
        redisTemplate.delete(KEY_PREFIX + accountId);
    }
}
