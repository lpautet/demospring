import React, { useState, useRef, useEffect } from 'react';
import './Chat.css';

function Chat() {
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const messagesEndRef = useRef(null);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    useEffect(() => {
        scrollToBottom();
    }, [messages]);

    const sendMessage = async (e) => {
        e.preventDefault();
        if (!input.trim() || isLoading) return;

        const userMessage = input.trim();
        setInput('');

        // Add user message to chat
        setMessages(prev => [...prev, { role: 'user', content: userMessage }]);
        setIsLoading(true);

        try {
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + sessionStorage.getItem('token')
                },
                body: JSON.stringify({ message: userMessage })
            });

            if (response.ok) {
                const data = await response.json();
                setMessages(prev => [...prev, { role: 'assistant', content: data.response }]);
            } else {
                setMessages(prev => [...prev, {
                    role: 'assistant',
                    content: 'Sorry, I encountered an error. Please try again.',
                    error: true
                }]);
            }
        } catch (error) {
            console.error('Error sending message:', error);
            setMessages(prev => [...prev, {
                role: 'assistant',
                content: 'Failed to connect to the server. Please check your connection.',
                error: true
            }]);
        } finally {
            setIsLoading(false);
        }
    };

    const clearHistory = async () => {
        try {
            await fetch('/api/chat/history', {
                method: 'DELETE',
                headers: {
                    'Authorization': 'Bearer ' + sessionStorage.getItem('token')
                }
            });
            setMessages([]);
        } catch (error) {
            console.error('Error clearing history:', error);
        }
    };

    return (
        <div className="chat-container">
            <div className="chat-header">
                <h2>🤖 AI Assistant</h2>
                <button onClick={clearHistory} className="clear-btn">
                    Clear History
                </button>
            </div>
            
            <div className="chat-messages">
                {messages.length === 0 && (
                    <div className="chat-welcome">
                        <p>👋 Hello! I'm your AI assistant.</p>
                        <p>Ask me anything about your Netatmo weather data!</p>
                    </div>
                )}
                
                {messages.map((msg, index) => (
                    <div key={index} className={`message ${msg.role} ${msg.error ? 'error' : ''}`}>
                        <div className="message-content">
                            {msg.content}
                        </div>
                    </div>
                ))}
                
                {isLoading && (
                    <div className="message assistant loading">
                        <div className="message-content">
                            <span className="typing-indicator">
                                <span></span>
                                <span></span>
                                <span></span>
                            </span>
                        </div>
                    </div>
                )}
                
                <div ref={messagesEndRef} />
            </div>
            
            <form onSubmit={sendMessage} className="chat-input-form">
                <input
                    type="text"
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    placeholder="Type your message..."
                    className="chat-input"
                    disabled={isLoading}
                />
                <button type="submit" disabled={isLoading || !input.trim()} className="send-btn">
                    Send
                </button>
            </form>
        </div>
    );
}

export default Chat;
