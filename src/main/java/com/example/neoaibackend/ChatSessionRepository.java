package com.example.neoaibackend;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {
    Optional<ChatSession> findBySessionId(String sessionId);
    void deleteBySessionId(String sessionId);
}