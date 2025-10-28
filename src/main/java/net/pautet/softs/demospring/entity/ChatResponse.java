package net.pautet.softs.demospring.entity;

public record ChatResponse(
        String response,
        int historySize
) {}
