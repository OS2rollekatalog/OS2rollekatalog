package dk.digitalidentity.rc.attestation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
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
        //noinspection unchecked
        return (T) readOnlyTemplate.execute((TransactionCallback<?>) status -> supplier.get());
    }

}
