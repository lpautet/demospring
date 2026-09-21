package net.pautet.softs.demospring.automation.internal;

import net.pautet.softs.demospring.automation.internal.Tasks;
import net.pautet.softs.demospring.operations.MessageService;
import net.pautet.softs.demospring.weather.NetatmoService;
import net.pautet.softs.demospring.datacloud.SalesforceService;
import net.pautet.softs.demospring.automation.internal.SchedulingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class TasksTest {

    private SalesforceService salesforceService;
    private NetatmoService netatmoService;
    private SchedulingService schedulingService;
    private MessageService messageService;
    private Tasks tasks;

    @BeforeEach
    void setUp() {
        salesforceService = mock(SalesforceService.class);
        netatmoService = mock(NetatmoService.class);
        schedulingService = mock(SchedulingService.class);
        messageService = mock(MessageService.class);
        tasks = new Tasks(salesforceService, netatmoService, schedulingService, messageService);
    }

    @Test
    void scheduleNetatmoToDataCloud_WhenShouldExecute_AndSalesforceConfigured() throws Exception {
        // Arrange
        when(schedulingService.shouldExecuteNetatmoToDataCloud(anyLong())).thenReturn(true);
        when(salesforceService.isConfigured()).thenReturn(true);

        Map<String, Object> metric = new HashMap<>();
        metric.put("temperature", 20.5);
        List<Map<String, Object>> metrics = Arrays.asList(metric);
        when(netatmoService.getNetatmoMetrics()).thenReturn(metrics);

        // Act
        tasks.scheduleNetatmoToDataCloud();

        // Assert
        verify(salesforceService).pushToDataCloud(metrics);
        verify(schedulingService).updateNetatmoToDataCloudExecutionTime();
        verify(messageService, never()).info(any());
        verify(messageService, never()).error(any());
    }

    @Test
    void scheduleNetatmoToDataCloud_WhenShouldExecute_ButSalesforceNotConfigured() throws IOException {
        // Arrange
        when(schedulingService.shouldExecuteNetatmoToDataCloud(anyLong())).thenReturn(true);
        when(salesforceService.isConfigured()).thenReturn(false);

        // Act
        tasks.scheduleNetatmoToDataCloud();

        // Assert
        verify(salesforceService, never()).pushToDataCloud(any());
        verify(schedulingService).updateNetatmoToDataCloudExecutionTime();
        verify(messageService).info("Salesforce configuration not available, skipping data push to Data Cloud");
    }

    @Test
    void scheduleNetatmoToDataCloud_WhenShouldNotExecute() throws IOException {
        // Arrange
        when(schedulingService.shouldExecuteNetatmoToDataCloud(anyLong())).thenReturn(false);

        // Act
        tasks.scheduleNetatmoToDataCloud();

        // Assert
        verify(salesforceService, never()).pushToDataCloud(any());
        verify(schedulingService, never()).updateNetatmoToDataCloudExecutionTime();
        verify(messageService, never()).info(any());
        verify(messageService, never()).error(any());
    }

    @Test
    void scheduleNetatmoToDataCloud_WhenExceptionOccurs() throws Exception {
        // Arrange
        when(schedulingService.shouldExecuteNetatmoToDataCloud(anyLong())).thenReturn(true);
        when(salesforceService.isConfigured()).thenReturn(true);
        when(netatmoService.getNetatmoMetrics()).thenThrow(new IOException("Test exception"));

        // Act
        tasks.scheduleNetatmoToDataCloud();

        // Assert
        verify(salesforceService, never()).pushToDataCloud(any());
        verify(schedulingService).updateNetatmoToDataCloudExecutionTime();
        verify(messageService).error("Error pushing to Data Cloud: Test exception");
    }

    @Test
    void scheduleMessageCleanup_WhenShouldExecute() {
        // Arrange
        when(schedulingService.shouldExecuteMessageCleanup(anyLong())).thenReturn(true);

        // Act
        tasks.scheduleMessageCleanup();

        // Assert
        verify(schedulingService).updateMessageCleanupExecutionTime();
    }

    @Test
    void scheduleMessageCleanup_WhenShouldNotExecute() {
        // Arrange
        when(schedulingService.shouldExecuteMessageCleanup(anyLong())).thenReturn(false);

        // Act
        tasks.scheduleMessageCleanup();

        // Assert
        verify(schedulingService, never()).updateMessageCleanupExecutionTime();
    }

    @Test
    void scheduleMetricsCollection_WhenShouldExecute() {
        // Arrange
        when(schedulingService.shouldExecuteMetricsCollection(anyLong())).thenReturn(true);

        // Act
        tasks.scheduleMetricsCollection();

        // Assert
        verify(schedulingService).updateMetricsCollectionExecutionTime();
    }

    @Test
    void scheduleMetricsCollection_WhenShouldNotExecute() {
        // Arrange
        when(schedulingService.shouldExecuteMetricsCollection(anyLong())).thenReturn(false);

        // Act
        tasks.scheduleMetricsCollection();

        // Assert
        verify(schedulingService, never()).updateMetricsCollectionExecutionTime();
    }
}
