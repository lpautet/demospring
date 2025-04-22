package net.pautet.softs.demospring;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.Message;
import net.pautet.softs.demospring.repository.MessageRepository;
import net.pautet.softs.demospring.service.NetatmoService;
import net.pautet.softs.demospring.service.SalesforceService;
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

    @Scheduled(fixedRate = 600000)
    public void scheduleNetatmoToDataCloud() {
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
            log.info("Scheduled task completed successfully");
        } catch (Exception e) {
            Message message = new Message("Error pushing to Data Cloud: " + e.getMessage(), "error", "server");
            messageRepository.save(message);
            log.error("Error in scheduled task: ", e);
        }
    }


}
