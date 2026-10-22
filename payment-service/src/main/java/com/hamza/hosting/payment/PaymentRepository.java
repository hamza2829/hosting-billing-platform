package com.hamza.hosting.payment;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.invoiceId=:id")
  Optional<Payment> locked(UUID id);
}
