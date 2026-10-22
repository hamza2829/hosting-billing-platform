package com.hamza.hosting.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class SearchQueryTest {
  @Test
  void combinesFiltersAndFullText() {
    var id = UUID.randomUUID();
    var query =
        SearchQuery.build(
                id,
                null,
                "PAID",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"),
                "hosting",
                false)
            .toString();
    assertTrue(query.contains(id.toString()));
    assertTrue(query.contains("multi_match"));
    assertTrue(query.contains("PAID"));
    assertTrue(query.contains("gte"));
  }

  @Test
  void rejectsReversedDates() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SearchQuery.build(null, null, null, Instant.MAX, Instant.MIN, null, false));
  }

  @Test
  void rejectsUnknownStatus() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SearchQuery.build(null, null, "UNKNOWN", null, null, null, false));
  }

  @Test
  void ordersInvoiceHistoryAscending() {
    var query = SearchQuery.build(null, UUID.randomUUID(), null, null, null, null, true);
    assertTrue(query.get("sort").toString().contains("asc"));
  }
}
