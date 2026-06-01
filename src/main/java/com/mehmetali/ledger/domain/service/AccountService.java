package com.mehmetali.ledger.domain.service;

import com.mehmetali.ledger.domain.model.Account;
import com.mehmetali.ledger.domain.model.AccountStatus;
import com.mehmetali.ledger.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public Account createAccount(Account account) {
        account.setStatus(AccountStatus.ACTIVE);
        return accountRepository.save(account);
    }
}
