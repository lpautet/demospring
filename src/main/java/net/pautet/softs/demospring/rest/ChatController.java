package net.pautet.softs.demospring.rest;

import lombok.extern.slf4j.Slf4j;
import net.pautet.softs.demospring.entity.ChatRequest;
import net.pautet.softs.demospring.entity.ChatResponse;
import net.pautet.softs.demospring.entity.User;
import net.pautet.softs.demospring.service.ChatService;
import net.pautet.softs.demospring.service.TradingChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final TradingChatService tradingChatService;

    public ChatController(ChatService chatService, TradingChatService tradingChatService) {
        this.chatService = chatService;
        this.tradingChatService = tradingChatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, Principal principal) {
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        String userId = user.getUsername();
        
        log.info("Chat request from user: {} - Message: {}", userId, request.message());
        
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        String response = chatService.chat(userId, request.message());
        int historySize = chatService.getHistorySize(userId);
        
        return ResponseEntity.ok(new ChatResponse(response, historySize));
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(Principal principal) {
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        String userId = user.getUsername();
        
        log.info("Clearing chat history for user: {}", userId);
        chatService.clearHistory(userId);
        
        return ResponseEntity.ok().build();
    }

    // ==================== Trading Chat Endpoints ====================

    @PostMapping("/trading")
    public ResponseEntity<ChatResponse> tradingChat(@RequestBody ChatRequest request, Principal principal) {
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        String userId = user.getUsername();
        
        log.info("Trading chat request from user: {} - Message: {}", userId, request.message());
        
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        String response = tradingChatService.chat(userId, request.message());
        int historySize = tradingChatService.getHistorySize(userId);
        
        return ResponseEntity.ok(new ChatResponse(response, historySize));
    }

    @DeleteMapping("/trading/history")
    public ResponseEntity<Void> clearTradingHistory(Principal principal) {
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        String userId = user.getUsername();
        
        log.info("Clearing trading chat history for user: {}", userId);
        tradingChatService.clearHistory(userId);
        
        return ResponseEntity.ok().build();
    }
}
