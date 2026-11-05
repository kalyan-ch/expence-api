package com.expence.expenceApi.service;

import com.expence.expenceApi.config.NotifyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Posts to a Discord channel webhook: one embed per message, no bot. */
@Slf4j
@Component
public class DiscordNotifier {

    public record Message(String title, String url, List<Field> fields) {}

    public record Field(String name, String value) {}

    private static final int AMBER = 0xF59E0B;
    private static final long MAX_WAIT_MS = 10_000;

    private final RestClient client;
    private final String webhookUrl;

    public DiscordNotifier(RestClient.Builder builder, NotifyProperties props) {
        this.client = builder.build();
        this.webhookUrl = props.webhookUrl();
    }

    public boolean enabled() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    /** True once Discord accepts it. Failures are logged, never thrown: a reminder must not break anything else. */
    public boolean send(Message message) {
        if (!enabled()) {
            log.info("No Discord webhook set; not sending '{}'", message.title());
            return false;
        }
        Map<String, Object> body = Map.of("embeds", List.of(embed(message)));
        try {
            try {
                post(body);
            } catch (HttpClientErrorException.TooManyRequests e) {
                pause(e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After"));
                post(body);
            }
            return true;
        } catch (RestClientException e) {
            log.warn("Discord did not accept '{}': {}", message.title(), e.getMessage());
            return false;
        }
    }

    private void post(Map<String, Object> body) {
        client.post().uri(URI.create(webhookUrl)).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
    }

    private static Map<String, Object> embed(Message message) {
        Map<String, Object> embed = new LinkedHashMap<>();
        embed.put("title", message.title());
        if (message.url() != null) embed.put("url", message.url());
        embed.put("color", AMBER);
        embed.put("fields", message.fields().stream().map(f -> Map.of("name", f.name(), "value", f.value())).toList());
        return embed;
    }

    /** Discord's Retry-After is in seconds, possibly fractional. */
    private static void pause(String retryAfter) {
        try {
            long ms = retryAfter == null ? 1000 : (long) (Double.parseDouble(retryAfter) * 1000);
            Thread.sleep(Math.min(ms, MAX_WAIT_MS));
        } catch (NumberFormatException e) {
            log.debug("Unreadable Retry-After '{}'; retrying now", retryAfter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
