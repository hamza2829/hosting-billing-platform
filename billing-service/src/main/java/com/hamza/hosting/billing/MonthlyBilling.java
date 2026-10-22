package com.hamza.hosting.billing;
import com.hamza.hosting.common.Trace;
import java.time.*;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class MonthlyBilling {
    private final SubscriptionRepository subscriptions; private final BillingOperations operations;
    public MonthlyBilling(SubscriptionRepository s,BillingOperations o) {subscriptions=s;operations=o;}
    @Scheduled(cron="${billing.monthly-cron}",zone="${billing.zone}") public void run() {
        var period=YearMonth.now(ZoneOffset.UTC);
        try(var ignored=Trace.scope("monthly-"+period)) {
            for(var s:subscriptions.findByActiveTrueAndStartsOnLessThanEqual(period.atEndOfMonth())) {
                try {operations.generate(s.id,period);} catch(Exception e) {LoggerFactory.getLogger(getClass()).error("Monthly billing failed subscriptionId={}",s.id,e);}
            }
        }
    }
}
