package net.pautet.softs.demospring;

import net.pautet.softs.demospring.entity.Message;
import net.pautet.softs.demospring.repository.MessageRepository;
import net.pautet.softs.demospring.service.NetatmoService;
import net.pautet.softs.demospring.service.SalesforceService;
import net.pautet.softs.demospring.service.SchedulingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TasksTest {

    private SalesforceService salesforceService;
    private NetatmoService netatmoService;
    private MessageRepository messageRepository;
    private SchedulingService schedulingService;
    private Tasks tasks;

    @BeforeEach
    void setUp() {
        salesforceService = mock(SalesforceService.class);
        netatmoService = mock(NetatmoService.class);
        messageRepository = mock(MessageRepository.class);
        schedulingService = mock(SchedulingService.class);
        tasks = new Tasks(salesforceService, netatmoService, messageRepository, schedulingService);
    }

    @Test
    void scheduleNetatmoToDataCloud_WhenShouldExecute_AndSalesforceConfigured() throws Exception {
        // Arrange
        when(schedulingService.shouldExecuteNetatmoToDataCloud(anyLong())).thenReturn(true);
        when(System.getenv("SF_PRIVATE_KEY")).thenReturn("test-key");
        
        Map<String, Object> metric = new HashMap<>();
        metric.put("temperature", 20.5);
        List<Map<String, Object>> metrics = Arrays.asList(metric);
        when(netatmoService.getNetatmoMetrics()).thenReturn(metrics);

        // Act
        tasks.scheduleNetatmoToDataCloud();

        // Assert
        verify(salesforceService).pushToDataCloud(metrics);
        verify(schedulingService).updateNetatmoToDataCloudExecutionTime();
        verify(messageRepository, never()).save(any());
    }

    @Test
    void scheduleNetatmoToDataCloud_WhenShouldExecute_ButSalesforceNotConfigured() throws IOException {
        // Arrange
        when(schedulingService.shouldExecuteNetatmoToDataCloud(anyLong())).thenReturn(true);
        when(System.getenv("SF_PRIVATE_KEY")).thenReturn(null);

        // Act
        tasks.scheduleNetatmoToDataCloud();

        // Assert
        verify(salesforceService, never()).pushToDataCloud(any());
        verify(schedulingService, never()).updateNetatmoToDataCloudExecutionTime();
        
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        
        Message savedMessage = messageCaptor.getValue();
        assertEquals("Salesforce configuration not available, skipping data push to Data Cloud", savedMessage.getMessage());
        assertEquals("info", savedMessage.getSeverity());
        assertEquals("server", savedMessage.getSource());
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
        verify(messageRepository, never()).save(any());
    }

    @Test
    void scheduleNetatmoToDataCloud_WhenExceptionOccurs() throws Exception {
        // Arrange
        when(schedulingService.shouldExecuteNetatmoToDataCloud(anyLong())).thenReturn(true);
        when(System.getenv("SF_PRIVATE_KEY")).thenReturn("test-key");
        when(netatmoService.getNetatmoMetrics()).thenThrow(new IOException("Test exception"));

        // Act
        tasks.scheduleNetatmoToDataCloud();

        // Assert
        verify(salesforceService, never()).pushToDataCloud(any());
        verify(schedulingService, never()).updateNetatmoToDataCloudExecutionTime();
        
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        
        Message savedMessage = messageCaptor.getValue();
        assertEquals("Error pushing to Data Cloud: Test exception", savedMessage.getMessage());
        assertEquals("error", savedMessage.getSeverity());
        assertEquals("server", savedMessage.getSource());
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