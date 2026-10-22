package com.hamza.hosting.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SearchController {
  private final ElasticAudit audit;

  public SearchController(ElasticAudit audit) {
    this.audit = audit;
  }

  @GetMapping("/search")
  public JsonNode search(
      @RequestParam(required = false) UUID customer,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) String q) {
    return audit.search(SearchQuery.build(customer, null, status, from, to, q, false));
  }

  @GetMapping("/invoices/{id}/audit")
  public JsonNode history(@PathVariable UUID id) {
    return audit.search(SearchQuery.build(null, id, null, null, null, null, true));
  }

  @GetMapping("/alerts")
  public JsonNode alerts() {
    return audit.search(
        Map.of(
            "size",
            100,
            "query",
            Map.of("terms", Map.of("status", List.of("FAILED", "DEAD_LETTER", "INVALID"))),
            "sort",
            List.of(Map.of("timestamp", "desc"))));
  }
}
