package com.example.neoaibackend;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "chat_sessions")
public class ChatSession {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String sessionId;
    
    private List<ChatMessage> history = new ArrayList<>();

    public ChatSession() {}
    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() { return sessionId; }
    public List<ChatMessage> getHistory() { return history; }
}