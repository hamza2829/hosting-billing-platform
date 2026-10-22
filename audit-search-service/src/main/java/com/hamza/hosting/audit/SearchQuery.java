package com.hamza.hosting.audit;
import java.time.Instant;
import java.util.*;
public final class SearchQuery {
    private SearchQuery() {}
    public static Map<String,Object> build(UUID customer,UUID invoice,String status,Instant from,Instant to,String text,boolean history) {
        if(from!=null&&to!=null&&from.isAfter(to))throw new IllegalArgumentException("Invalid date range");
        if(status!=null&&!Set.of("OPEN","PAID","FAILED","DEAD_LETTER","INVALID").contains(status))throw new IllegalArgumentException("Invalid status");
        if(text!=null&&text.length()>200)throw new IllegalArgumentException("Query too long");
        List<Object> filters=new ArrayList<>();
        if(customer!=null)filters.add(Map.of("term",Map.of("customerId",customer.toString())));
        if(invoice!=null)filters.add(Map.of("term",Map.of("invoiceId",invoice.toString())));
        if(status!=null)filters.add(Map.of("term",Map.of("status",status)));
        Map<String,Object> range=new LinkedHashMap<>();if(from!=null)range.put("gte",from.toString());if(to!=null)range.put("lte",to.toString());
        if(!range.isEmpty())filters.add(Map.of("range",Map.of("timestamp",range)));
        Object must=text==null||text.isBlank()?Map.of("match_all",Map.of()):Map.of("multi_match",Map.of("query",text,"fields",List.of("detail","type","status")));
        return Map.of("size",100,"track_total_hits",true,"query",Map.of("bool",Map.of("filter",filters,"must",List.of(must))),"sort",List.of(Map.of("timestamp",history?"asc":"desc")));
    }
}
