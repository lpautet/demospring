package net.pautet.softs.demospring.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final Map<String, List<Message>> conversationHistory = new ConcurrentHashMap<>();
    
    private static final String SYSTEM_MESSAGE = """
            You are a helpful AI assistant for a Netatmo weather station monitoring application.
            
            You have access to real-time weather data through function calling tools:
            - getHomesData: Get all homes, devices, and modules configuration
            - getHomeStatus: Get current sensor readings (temperature, humidity, CO2, pressure, etc.)
            - getMeasure: Get historical measurements over the last 24 hours
            
            When the user asks about weather data, temperatures, humidity, or any sensor readings:
            1. Use the function calls to fetch real data
            2. The username parameter should ALWAYS be the current user's username (you'll receive this in context)
            3. Interpret the JSON responses and present the data in a friendly, conversational way
            4. Include units (°C, %, ppm, mbar) when presenting measurements
            5. If you need home_id or device_id, first call getHomesData to discover them
            
            Available measurement types: Temperature, Humidity, CO2, Pressure, Noise, Rain
            
            Be concise, friendly, and helpful. Convert technical data into easy-to-understand insights.
            """;

    public ChatService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_MESSAGE)
                .defaultFunctions("getHomesData", "getHomeStatus", "getMeasure")
                .build();
    }

    /**
     * Send a message to the AI and get a response
     */
    public String chat(String userId, String userMessage) {
        log.debug("Processing chat message for user: {} - Message: {}", userId, userMessage);
        
        // Get or create conversation history for this user
        List<Message> history = conversationHistory.computeIfAbsent(userId, k -> {
            List<Message> newHistory = new ArrayList<>();
            // Add user context as first message
            newHistory.add(new SystemMessage("Current user context: username=" + userId));
            return newHistory;
        });
        
        // Add user message to history
        UserMessage message = new UserMessage(userMessage);
        history.add(message);
        
        // Keep only last 10 messages + system context message to avoid token limits
        if (history.size() > 11) {
            List<Message> trimmedHistory = new ArrayList<>();
            // Keep the first message (username context)
            trimmedHistory.add(history.get(0));
            // Add last 10 messages
            trimmedHistory.addAll(history.subList(history.size() - 10, history.size()));
            history = trimmedHistory;
            conversationHistory.put(userId, history);
        }
        
        try {
            // Get AI response with explicit temperature override for gpt-5-mini
            String response = chatClient.prompt()
                    .messages(history)
                    .options(org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .temperature(1.0)  // gpt-5-mini only supports 1.0
                            .build())
                    .call()
                    .content();
            
            log.debug("AI response for user {}: {}", userId, response);
            
            return response;
        } catch (Exception e) {
            log.error("Error processing chat message for user {}: {}", userId, e.getMessage(), e);
            return "I'm sorry, I encountered an error processing your request. Please try again.";
        }
    }

    /**
     * Clear conversation history for a user
     */
    public void clearHistory(String userId) {
        conversationHistory.remove(userId);
        log.info("Cleared conversation history for user: {}", userId);
    }

    /**
     * Get conversation history size for a user
     */
    public int getHistorySize(String userId) {
        return conversationHistory.getOrDefault(userId, new ArrayList<>()).size();
    }
}
