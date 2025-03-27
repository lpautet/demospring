package net.pautet.softs.demospring;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Scheduled(fixedRate = 600000)
    public void scheduleNetatmoToDataCloud() {
        try {
            log.info("Starting scheduled Netatmo data fetch and push at {}", new java.util.Date());
            List<Map<String, Object>> metrics = netatmoService.getNetatmoMetrics();
            salesforceService.pushToDataCloud(metrics);
            log.info("Scheduled task completed successfully");
        } catch (Exception e) {
            log.error("Error in scheduled task: ", e);
        }
    }

}
