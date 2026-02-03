import io.smallrye.mutiny.Multi;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletableFuture;

@Path("/chat")
public class ChatStreamResource {

    private static final Logger LOG = Logger.getLogger(ChatStreamResource.class);

    @Inject
    ChatApiClient chatApiClient;

    @POST
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> streamChat(ChatRequest request) {
        
        StringBuilder responseAccumulator = new StringBuilder();
        
        return chatApiClient.streamChat(request)
            .onItem().invoke(chunk -> {
                // Accumulate response for later logging
                responseAccumulator.append(chunk);
            })
            .onFailure().transform(throwable -> {
                LOG.error("Chat API streaming failed", throwable);
                return new WebApplicationException(
                    "Chat service error: " + throwable.getMessage(),
                    Response.Status.SERVICE_UNAVAILABLE
                );
            })
            .onCompletion().invoke(() -> {
                // Log complete response asynchronously
                logResponseAsync(responseAccumulator.toString());
            });
    }

    private void logResponseAsync(String fullResponse) {
        CompletableFuture.runAsync(() -> {
            try {
                LOG.info("=== Complete Chat Response ===");
                LOG.info("Content: " + fullResponse);
            } catch (Exception e) {
                LOG.error("Failed to log response", e);
            }
        });
    }

    @RegisterRestClient(configKey = "chat-api")
    public interface ChatApiClient {
        
        @POST
        @Path("/chat/completions")
        @Produces(MediaType.SERVER_SENT_EVENTS)
        Multi<String> streamChat(ChatRequest request);
    }

    public static class ChatRequest {
        private String message;
        
        // getters/setters
    }
}
