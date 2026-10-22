package com.hamza.hosting.billing;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Subscription s where s.id=:id")
  Optional<Subscription> locked(UUID id);

  List<Subscription> findByActiveTrueAndStartsOnLessThanEqual(LocalDate end);
}
