package net.pautet.softs.demospring;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.Message;
import net.pautet.softs.demospring.exception.NetatmoApiException;
import net.pautet.softs.demospring.repository.MessageRepository;
import net.pautet.softs.demospring.service.NetatmoService;
import net.pautet.softs.demospring.service.SalesforceService;
import net.pautet.softs.demospring.service.SchedulingService;
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
    private final MessageRepository messageRepository;
    private final SchedulingService schedulingService;
    private static final long NETATMO_TO_DATACLOUD_INTERVAL = 600000; // 10 minutes in milliseconds
    private static final long MESSAGE_CLEANUP_INTERVAL = 3600000; // 1 hour in milliseconds
    private static final long METRICS_COLLECTION_INTERVAL = 300000; // 5 minutes in milliseconds

    @Scheduled(fixedRate = 60000) // Check every minute
    public void scheduleNetatmoToDataCloud() {
        if (!schedulingService.shouldExecuteNetatmoToDataCloud(NETATMO_TO_DATACLOUD_INTERVAL)) {
            return;
        } else {
            // we update even if it fails, otherwise we risk reaching user limits
            schedulingService.updateNetatmoToDataCloudExecutionTime();
        }

        try {
            // Check if Salesforce configuration is available
            if (System.getenv("SF_PRIVATE_KEY") == null) {
                log.info("Salesforce configuration not available, skipping data push to Data Cloud");
                Message message = new Message("Salesforce configuration not available, skipping data push to Data Cloud", "info", "server");
                messageRepository.save(message);
                return;
            }

            log.info("Starting scheduled Netatmo data fetch and push at {}", new java.util.Date());
            List<Map<String, Object>> metrics = netatmoService.getNetatmoMetrics();
            salesforceService.pushToDataCloud(metrics);
            log.info("Scheduled task completed successfully. Current hour Netatmo API request count: {}", 
                    netatmoService.getCurrentHourRequestCount());
        } catch (NetatmoApiException e) {
            String errorMessage = e.getError() != null && e.getError().error() != null ?
                e.getError().error().message() : "Unknown Netatmo API error";
            Message message = new Message("Netatmo API Error: " + errorMessage, "error", "server");
            messageRepository.save(message);
            log.error("Netatmo API error in scheduled task: {}. Current hour request count: {}", 
                    errorMessage, netatmoService.getCurrentHourRequestCount());
        } catch (Exception e) {
            String errorMsg = "";
            Throwable c = e;
            while (c != null) {
                errorMsg += c.getMessage() + "->";
                c = c.getCause();
            }
            errorMsg += "End.";

            Message message = new Message("Error pushing to Data Cloud: " + errorMsg, "error", "server");
            messageRepository.save(message);
            log.error("Error in scheduled task: {}. Current hour request count: {}", 
                    errorMsg, netatmoService.getCurrentHourRequestCount());
        }
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    public void scheduleMessageCleanup() {
        if (!schedulingService.shouldExecuteMessageCleanup(MESSAGE_CLEANUP_INTERVAL)) {
            return;
        }

        try {
            log.info("Starting message cleanup at {}", new java.util.Date());
            // TODO: Implement message cleanup logic
            schedulingService.updateMessageCleanupExecutionTime();
            log.info("Message cleanup completed successfully");
        } catch (Exception e) {
            log.error("Error in message cleanup task: ", e);
        }
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    public void scheduleMetricsCollection() {
        if (!schedulingService.shouldExecuteMetricsCollection(METRICS_COLLECTION_INTERVAL)) {
            return;
        }

        try {
            log.info("Starting metrics collection at {}", new java.util.Date());
            // TODO: Implement metrics collection logic
            schedulingService.updateMetricsCollectionExecutionTime();
            log.info("Metrics collection completed successfully");
        } catch (Exception e) {
            log.error("Error in metrics collection task: ", e);
        }
    }
}
