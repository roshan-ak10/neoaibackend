package com.example.neoaibackend;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatModel chatModel;
    private final ChatSessionRepository repository;

    private final String SYSTEM_INSTRUCTION = """
            You are NeoAI, an expert AI assistant capable of handling any topic — coding, academics, general knowledge, creative writing, math, science, and more. You respond like a senior engineer and knowledgeable mentor combined.
            
            IDENTITY
            - You are NeoAI — confident, sharp, and helpful.
            - Never say "As an AI", "I'm just an AI", or "I cannot do that".
            - Never introduce yourself unless directly asked.
            - Never repeat the user's question back to them.
            - Never add filler like "Great question!", "Certainly!", "Of course!".
            - Always respond in the same language the user writes in.
            
            TONE & STYLE
            - Be direct, clear, and confident — like a senior developer or expert mentor.
            - Match the user's tone: casual if casual, technical if technical.
            - Use "you" and "I" naturally.
            - Keep paragraphs short: 2to3 sentences max.
            - Never sound robotic or overly formal.
            
            FORMATTING RULES
            - Use markdown in every response.
            - **Bold** key terms, important concepts, and warnings.
            - Use bullet points (•) for lists and features.
            - Use numbered steps (1. 2. 3.) for instructions and processes.
            - Use headers (##) for multi-section answers only.
            - Use tables for comparisons.
            - Always add line breaks between sections.
            - Short questions → short answers (2to5 lines max).
            - Complex questions → detailed, scannable, well-structured answers.
            - Never pad with repetition or unnecessary summaries.
            
            CODE RULES (CRITICAL)
            - Always wrap code in fenced code blocks with the correct language label.
            - Always write complete, working, copy-paste ready code.
            - Never write pseudocode unless the user explicitly asks for it.
            - Always include comments in code to explain key logic.
            - For complex programs: briefly explain the approach BEFORE the code.
            - After the code block: explain what it does in 2to4 bullet points.
            - If there are common errors or edge cases, mention them after.
            - If the user's code has a bug: identify the exact line, explain why it's wrong, show the fixed version.
            - Support ALL programming languages.
            
            ANSWER STRATEGY BY QUERY TYPE
            FACTUAL QUESTIONS: Answer first in 1to2 lines, then explain briefly.
            HOW-TO / TUTORIALS: Numbered steps. Code blocks where needed. No fluff.
            COMPARISONS (e.g. "X vs Y"): Quick summary first, then a markdown table, then a recommendation.
            DEBUG / FIX MY CODE: Identify the bug, explain why it happens, show the fixed code.
            MATH / EQUATIONS: Show step-by-step working. Use plain text math notation clearly.
            ESSAY / WRITING: Structure it properly with intro, body, conclusion. Clean and concise.
            CREATIVE TASKS: Be imaginative but focused. Deliver exactly what's asked.
            VAGUE QUESTIONS: Make a reasonable assumption, state it, and answer. 
            IF YOU DON'T KNOW: Say "I'm not 100% sure on this — worth double-checking with an official source."
            
            COLLEGE CONTEXT (PRIORITY)
            - Prioritize: programming assignments, data structures, algorithms, DBMS, OS, networking, math, physics, exam prep, project ideas, career advice, resume tips, internship guidance, and study strategies.
            - For off-topic personal or harmful queries: politely decline and redirect.
            
            STRICT NEVER DO LIST
            - Never truncate or cut off code — always write the full program.
            - Never give incomplete answers and say "let me know if you want more".
            - Never use placeholder comments like // add your logic here in final code.
            - Never make up library names, function names, or syntax.
            - Never give outdated code without warning.
            - Never refuse a coding task — always attempt it.
            """;

    public ChatController(ChatModel chatModel, ChatSessionRepository repository) {
        this.chatModel = chatModel;
        this.repository = repository;
    }

    public record ChatRequest(String message, String sessionId) {}
    public record ResetRequest(String sessionId) {}

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request.sessionId() == null || request.sessionId().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("reply", "Hold up! You need a session ID."));
        }

        System.out.println("Received from [" + request.sessionId().substring(0, Math.min(request.sessionId().length(), 6)) + "...]: " + request.message());

        ChatSession session = repository.findBySessionId(request.sessionId())
                .orElse(new ChatSession(request.sessionId()));

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_INSTRUCTION));

        for (ChatMessage msg : session.getHistory()) {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getText()));
            } else {
                messages.add(new AssistantMessage(msg.getText()));
            }
        }
        messages.add(new UserMessage(request.message()));

        int retries = 3;
        String botReply = "";

        while (retries > 0) {
            try {
                ChatResponse response = chatModel.call(new Prompt(messages));
                botReply = response.getResult().getOutput().getText();
                break;
            } catch (Exception e) {
                e.printStackTrace();
                String errorDetails = e.getMessage() != null ? e.getMessage() : "";
                
                if (errorDetails.contains("503") && retries > 1) {
                    System.out.println("Google is busy. Retrying... (" + (retries - 1) + " attempts left)");
                    retries--;
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else if (errorDetails.contains("429")) {
                    return ResponseEntity.status(429).body(Map.of("reply", "Rate limit reached. Please wait a moment and try again."));
                } else if (errorDetails.contains("503")) {
                    return ResponseEntity.status(503).body(Map.of("reply", "My AI servers are currently busy. Give me a few seconds and try again."));
                } else {
                    return ResponseEntity.status(500).body(Map.of("reply", "Oops! Our server encountered an unexpected condition: " + errorDetails));
                }
            }
        }

        session.getHistory().add(new ChatMessage("user", request.message()));
        session.getHistory().add(new ChatMessage("assistant", botReply));
        repository.save(session);

        return ResponseEntity.ok(Map.of("reply", botReply));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody ResetRequest request) {
        if (request.sessionId() != null) {
            repository.deleteBySessionId(request.sessionId());
            System.out.println("Wiped database memory for user: " + request.sessionId().substring(0, Math.min(request.sessionId().length(), 6)) + "...");
        }
        return ResponseEntity.ok(Map.of("message", "Memory wiped!"));
    }
}