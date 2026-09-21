package net.pautet.softs.demospring.automation.internal;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.weather.NetatmoApiException;
import net.pautet.softs.demospring.operations.MessageService;
import net.pautet.softs.demospring.weather.NetatmoService;
import net.pautet.softs.demospring.datacloud.SalesforceService;
import net.pautet.softs.demospring.automation.internal.SchedulingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@AllArgsConstructor
public class Tasks {

    private final SalesforceService salesforceService;
    private final NetatmoService netatmoService;
    private final SchedulingService schedulingService;
    private final MessageService messageService;
    private static final long NETATMO_TO_DATACLOUD_INTERVAL = 300000; // 5 minutes
    private static final long MESSAGE_CLEANUP_INTERVAL = 3600000; // 1 hour in milliseconds
    private static final long METRICS_COLLECTION_INTERVAL = 300000; // 5 minutes in milliseconds

    @Scheduled(fixedRate = 60000) // Check every minute
    public void scheduleNetatmoToDataCloud() {
        if (!schedulingService.tryAcquireNetatmoToDataCloud(NETATMO_TO_DATACLOUD_INTERVAL)) {
            return;
        }
        try {
            // Check if Salesforce configuration is available
            if (!salesforceService.isConfigured()) {
                log.info("Salesforce configuration not available, skipping data push to Data Cloud");
                messageService.info("Salesforce configuration not available, skipping data push to Data Cloud");
                return;
            }
            log.info("Starting scheduled Netatmo data fetch and push at {}", new java.util.Date());
            List<Map<String, Object>> metrics = netatmoService.getNetatmoMetrics();
            salesforceService.pushToDataCloud(metrics);
            log.info("Scheduled task completed successfully. Current hour Netatmo API request count: {}",
                    netatmoService.getCurrentHourRequestCount());
        } catch (NetatmoApiException e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown Netatmo API error";
            messageService.error("Netatmo API Error: " + errorMessage);
            log.error("Netatmo API error in scheduled task: {}. Current hour request count: {}",
                    errorMessage, netatmoService.getCurrentHourRequestCount());
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            messageService.error("Error pushing to Data Cloud: " + errorMsg);
            log.error("Error in scheduled task: {}. Current hour request count: {}",
                    errorMsg, netatmoService.getCurrentHourRequestCount());
        }
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    public void scheduleMessageCleanup() {
        if (!schedulingService.tryAcquireMessageCleanup(MESSAGE_CLEANUP_INTERVAL)) {
            return;
        }

        try {
            log.info("Starting message cleanup at {}", new java.util.Date());
            // TODO: Implement message cleanup logic
            log.info("Message cleanup completed successfully");
        } catch (Exception e) {
            log.error("Error in message cleanup task: ", e);
        }
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    public void scheduleMetricsCollection() {
        if (!schedulingService.tryAcquireMetricsCollection(METRICS_COLLECTION_INTERVAL)) {
            return;
        }

        try {
            log.info("Starting metrics collection at {}", new java.util.Date());
            // TODO: Implement metrics collection logic
            log.info("Metrics collection completed successfully");
        } catch (Exception e) {
            log.error("Error in metrics collection task: ", e);
        }
    }
}
