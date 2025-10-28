# AI Chat Integration with Spring AI and OpenAI

This application now includes an AI-powered chatbot interface using **Spring AI** and **OpenAI's GPT-4o-mini** model.

## Features

✅ **Chatbot Interface** - Beautiful, modern chat UI  
✅ **Conversation Memory** - Maintains chat history per user  
✅ **Low Cost** - Uses GPT-4o-mini for cost-effective AI interactions  
✅ **Spring AI Integration** - Built on Spring AI framework  
✅ **Secure** - User-authenticated chat sessions  
✅ **Ready for Tools** - Architecture ready for function calling  

## Prerequisites

### OpenAI API Key

1. **Get an OpenAI API Key**:
   - Go to https://platform.openai.com/api-keys
   - Create a new API key
   - Copy the key (starts with `sk-`)

2. **Set Environment Variable**:
   ```bash
   export OPENAI_API_KEY="sk-your-api-key-here"
   ```

### Configuration

The app is configured to use **GPT-4o-mini** (cost-effective model):

```properties
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o-mini
spring.ai.openai.chat.options.temperature=0.7
```

## Architecture

### Backend Components

#### 1. **ChatService** (`ChatService.java`)
- Manages AI interactions using Spring AI's `ChatClient`
- Maintains conversation history per user (last 10 messages)
- Handles errors gracefully
- System prompt: Configured to help with Netatmo weather data

#### 2. **ChatController** (`ChatController.java`)
- REST API endpoint: `POST /api/chat`
- Endpoint to clear history: `DELETE /api/chat/history`
- User authentication via JWT token
- Request/Response DTOs: `ChatRequest` and `ChatResponse`

#### 3. **Configuration**
- Spring AI BOM for dependency management
- OpenAI Spring Boot Starter auto-configuration
- Model configuration in `application.properties`

### Frontend Components

#### 1. **Chat Component** (`Chat.js`)
- Modern chat interface
- Real-time message updates
- Loading indicators
- Error handling
- Clear history button

#### 2. **Styling** (`Chat.css`)
- Gradient theme matching app design
- Smooth animations
- Responsive layout
- Typing indicator
- Custom scrollbar

#### 3. **Navigation**
- Tab-based navigation between Dashboard and AI Chat
- State management in `App.js`

## Usage

### Starting a Chat Session

1. **Navigate to the Chat page** by clicking the "🤖 AI Chat" button in the navigation bar

2. **Start chatting**:
   ```
   User: What's the current temperature outside?
   AI: [Will respond based on context]
   ```

3. **Clear conversation history** using the "Clear History" button

### API Endpoints

#### Send a Message
```http
POST /api/chat
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "message": "What's the temperature outside?"
}
```

**Response:**
```json
{
  "response": "Based on your Netatmo data...",
  "historySize": 2
}
```

#### Clear History
```http
DELETE /api/chat/history
Authorization: Bearer <jwt-token>
```

## Conversation Memory

- Each user has their own conversation history
- Stores last **10 messages** to avoid token limits
- History persists during the session
- Can be cleared manually via the UI or API

## Customization

### Change AI Model

Edit `application.properties`:
```properties
# Use GPT-4 for better quality (higher cost)
spring.ai.openai.chat.options.model=gpt-4

# Use GPT-3.5-turbo for lower cost
spring.ai.openai.chat.options.model=gpt-3.5-turbo
```

### Adjust Temperature

```properties
# More creative (0.0 - 2.0)
spring.ai.openai.chat.options.temperature=1.0

# More deterministic
spring.ai.openai.chat.options.temperature=0.3
```

### Modify System Prompt

Edit `ChatService.java`:
```java
private static final String SYSTEM_MESSAGE = """
        You are a helpful AI assistant...
        [Your custom prompt here]
        """;
```

## Next Steps: Adding Tools (Function Calling)

The architecture is ready for Spring AI function calling. Here's how to add tools:

### Example: Get Current Temperature

```java
@Component
public class NetatmoTools {
    
    @Bean
    @Description("Get current outdoor temperature from Netatmo station")
    public Function<Void, Double> getCurrentTemperature() {
        return (input) -> {
            // Fetch from your Netatmo service
            return netatmoService.getOutdoorTemperature();
        };
    }
}
```

Then register in `ChatService`:
```java
this.chatClient = ChatClient.builder(chatModel)
        .defaultSystem(SYSTEM_MESSAGE)
        .defaultFunctions("getCurrentTemperature") // Register function
        .build();
```

The AI will automatically call this function when users ask about temperature!

## Cost Estimation

**GPT-4o-mini pricing** (as of 2024):
- Input: $0.150 per 1M tokens
- Output: $0.600 per 1M tokens

**Example costs**:
- 100 conversations (~10 messages each): ~$0.02-0.05
- 1,000 conversations: ~$0.20-0.50

Very cost-effective for personal/small business use!

## Troubleshooting

### API Key Issues

**Error**: `401 Unauthorized`
- Check if `OPENAI_API_KEY` is set correctly
- Verify the API key is valid and active
- Ensure the key has billing enabled

### Chat Not Loading

**Error**: `Failed to connect to server`
- Verify JWT token is valid
- Check if user is authenticated
- Look for CORS issues in browser console

### Conversation History Not Working

- Check if user authentication is working
- Verify `userId` is being extracted correctly
- Check server logs for errors in `ChatService`

## Dependencies

Added to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
```

Spring AI version: `1.0.0-M5`  
Repository: Spring Milestones

## Security Best Practices

- ✅ **Never commit API keys** - Use environment variables
- ✅ **User authentication** - All endpoints require JWT token
- ✅ **Per-user isolation** - Each user has separate conversation history
- ✅ **Rate limiting** (TODO) - Consider adding rate limits for production
- ✅ **Input validation** - Validates message content before sending

## Resources

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [OpenAI API Reference](https://platform.openai.com/docs/api-reference)
- [GPT-4o-mini Model Card](https://platform.openai.com/docs/models/gpt-4o-mini)

---

**Ready to chat with your Netatmo data!** 🚀

Next step: Add function calling to let the AI query your weather data directly.
