package net.pautet.softs.demospring.controller;

import net.pautet.softs.demospring.entity.Message;
import net.pautet.softs.demospring.rest.ApiController;
import net.pautet.softs.demospring.service.MessageService;
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
    private ApiController apiController;

    @BeforeEach
    void setUp() {
        messageService = mock(MessageService.class);
        apiController = new ApiController(null, null, null, null, null, messageService, null);
    }

    @Test
    void getMessages_WhenSuccessful() {
        // Arrange
        Message message1 = createTestMessage("Test message 1", "info", "server");
        Message message2 = createTestMessage("Test message 2", "error", "client");
        List<Message> expectedMessages = Arrays.asList(message1, message2);
        when(messageService.getAllMessages()).thenReturn(expectedMessages);

        // Act
        ResponseEntity<List<Message>> response = apiController.getAllMessages();

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
        ResponseEntity<List<Message>> response = apiController.getAllMessages();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    private Message createTestMessage(String content, String severity, String source) {
        Message message = new Message();
        message.setMessage(content);
        message.setSeverity(severity);
        message.setSource(source);
        message.setTimestamp(Instant.now());
        return message;
    }
} 