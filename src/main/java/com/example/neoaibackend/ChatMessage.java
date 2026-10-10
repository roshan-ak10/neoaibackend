package com.example.neoaibackend;

public class ChatMessage {
    private String role;
    private String text;

    public ChatMessage() {}
    public ChatMessage(String role, String text) {
        this.role = role;
        this.text = text;
    }

    public String getRole() { return role; }
    public String getText() { return text; }
}