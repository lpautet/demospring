package net.pautet.softs.demospring.operations.internal.web;

import net.pautet.softs.demospring.operations.LogMessage;
import net.pautet.softs.demospring.operations.MessageService;
import net.pautet.softs.demospring.operations.internal.web.MessageController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageControllerTest {

    private MessageService messageService;
    private MessageController messageController;

    @BeforeEach
    void setUp() {
        messageService = mock(MessageService.class);
        messageController = new MessageController(messageService);
    }

    @Test
    void getMessages_WhenSuccessful() {
        // Arrange
        LogMessage logMessage1 = createTestMessage("Test message 1", "info", "server");
        LogMessage logMessage2 = createTestMessage("Test message 2", "error", "client");
        List<LogMessage> expectedLogMessages = Arrays.asList(logMessage1, logMessage2);
        when(messageService.getAllMessages()).thenReturn(expectedLogMessages);

        // Act
        ResponseEntity<List<LogMessage>> response = messageController.getAllMessages();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("Test message 1", response.getBody().get(0).getMessage());
        assertEquals("Test message 2", response.getBody().get(1).getMessage());
    }

    @Test
    void getMessages_WhenEmpty() {
        // Arrange
        when(messageService.getAllMessages()).thenReturn(Arrays.asList());

        // Act
        ResponseEntity<List<LogMessage>> response = messageController.getAllMessages();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    private LogMessage createTestMessage(String content, String severity, String source) {
        LogMessage logMessage = new LogMessage();
        logMessage.setMessage(content);
        logMessage.setSeverity(severity);
        logMessage.setSource(source);
        logMessage.setTimestamp(Instant.now());
        return logMessage;
    }
}
