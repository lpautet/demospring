package net.pautet.softs.demospring.operations.internal.web;

import lombok.RequiredArgsConstructor;
import net.pautet.softs.demospring.operations.LogMessage;
import net.pautet.softs.demospring.operations.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ResponseEntity<List<LogMessage>> getAllMessages() {
        return ResponseEntity.ok(messageService.getAllMessages());
    }
}
