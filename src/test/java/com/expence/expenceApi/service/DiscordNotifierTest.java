package com.expence.expenceApi.service;

import com.expence.expenceApi.config.NotifyProperties;
import com.expence.expenceApi.service.DiscordNotifier.Field;
import com.expence.expenceApi.service.DiscordNotifier.Message;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

class DiscordNotifierTest {

    private static final String URL = "https://discord.test/api/webhooks/1/abc";
    private static final Message MESSAGE = new Message("💸 Bills", "http://localhost:3000/bills", List.of(new Field("🟠 Due today", "Rent")));

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private DiscordNotifier notifier(String url) {
        return new DiscordNotifier(builder, new NotifyProperties(url, "0 0 8 * * *", "", 3, "http://localhost:3000"));
    }

    @Test
    void postsOneEmbed() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.embeds[0].title").value("💸 Bills"))
                .andExpect(jsonPath("$.embeds[0].url").value("http://localhost:3000/bills"))
                .andExpect(jsonPath("$.embeds[0].fields[0].name").value("🟠 Due today"))
                .andExpect(jsonPath("$.embeds[0].fields[0].value").value("Rent"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(notifier(URL).send(MESSAGE)).isTrue();
        server.verify();
    }

    @Test
    void retriesOnceWhenRateLimited() {
        server.expect(requestTo(URL)).andRespond(withTooManyRequests().header("Retry-After", "0.01"));
        server.expect(requestTo(URL)).andRespond(withSuccess());

        assertThat(notifier(URL).send(MESSAGE)).isTrue();
        server.verify();
    }

    @Test
    void aRejectedMessageReturnsFalseInsteadOfThrowing() {
        server.expect(times(1), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(notifier(URL).send(MESSAGE)).isFalse();
        server.verify();
    }

    @Test
    void aBlankWebhookSendsNothing() {
        DiscordNotifier notifier = notifier(" ");

        assertThat(notifier.enabled()).isFalse();
        assertThat(notifier.send(MESSAGE)).isFalse();
        server.verify();
    }
}
