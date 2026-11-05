package com.expence.expenceApi.service;

import com.expence.expenceApi.config.NotifyProperties;
import com.expence.expenceApi.dao.NotificationLogRepository;
import com.expence.expenceApi.exception.ConflictException;
import com.expence.expenceApi.service.DiscordNotifier.Field;
import com.expence.expenceApi.service.DiscordNotifier.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    static final String DIGEST = "DIGEST";

    private final DueBillService dueBillService;
    private final DiscordNotifier discord;
    private final NotificationLogRepository logRepository;
    private final NotifyProperties props;
    private final Clock clock;

    @Scheduled(cron = "${app.notify.cron}", zone = "${app.notify.zone}")
    public void daily() {
        sendDigest(LocalDate.now(clock));
    }

    /** The API only runs when started by hand, so starting it after the send time sends the digest it missed. */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUp() {
        LocalDateTime now = LocalDateTime.now(clock);
        if (missed(now, props.cron())) sendDigest(now.toLocalDate());
    }

    /**
     * At most once a day: the log row is claimed before sending, so a restart or the catch-up never
     * sends twice. A day with nothing due keeps its claim; a failed send gives it back for a later run.
     */
    public void sendDigest(LocalDate today) {
        if (!discord.enabled() || logRepository.claim(DIGEST, today.toString()) == 0) return;
        Optional<Message> digest = BillDigest.build(dueBillService.snapshot(today), props.leadDays(), props.appUrl());
        if (digest.isPresent() && !discord.send(digest.get())) logRepository.release(DIGEST, today.toString());
    }

    /** Today's digest on demand, ignoring the log, so the output can be checked by hand. */
    public boolean sendDigestNow() {
        LocalDate today = LocalDate.now(clock);
        return deliver(BillDigest.build(dueBillService.snapshot(today), props.leadDays(), props.appUrl())
                .orElseGet(() -> BillDigest.allClear(today, props.leadDays(), props.appUrl())));
    }

    public boolean sendTest() {
        return deliver(new Message("Expence is connected ✅", props.appUrl() + "/bills",
                List.of(new Field("Bill reminders", "The daily digest will arrive in this channel."))));
    }

    /** True if today's first scheduled run is already past. */
    static boolean missed(LocalDateTime now, String cron) {
        LocalDateTime first = CronExpression.parse(cron).next(now.toLocalDate().atStartOfDay().minusSeconds(1));
        return first != null && !first.isAfter(now);
    }

    private boolean deliver(Message message) {
        if (!discord.enabled()) throw new ConflictException("Set DISCORD_WEBHOOK_URL to send notifications");
        return discord.send(message);
    }
}
