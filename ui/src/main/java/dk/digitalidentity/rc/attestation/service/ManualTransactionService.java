package dk.digitalidentity.rc.attestation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Service
public class ManualTransactionService {
    private final TransactionTemplate readOnlyTemplate;

    public ManualTransactionService(final PlatformTransactionManager transactionManager) {
        readOnlyTemplate = new TransactionTemplate(transactionManager);
        readOnlyTemplate.setReadOnly(true);
    }

    public <T> T doInReadOnlyTransaction(final Supplier<T> supplier) {
        return readOnlyTemplate.execute(_ -> supplier.get());
    }
}