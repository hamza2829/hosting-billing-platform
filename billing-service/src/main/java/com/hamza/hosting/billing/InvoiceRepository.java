package com.hamza.hosting.billing;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface InvoiceRepository extends JpaRepository<Invoice,UUID> {
    Optional<Invoice> findBySubscriptionIdAndPeriod(UUID subscriptionId,String period);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Invoice i where i.id=:id") Optional<Invoice> locked(UUID id);
}
