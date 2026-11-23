package com.teamwork.backend.notice;

import com.teamwork.backend.common.ApiException;
import com.teamwork.backend.common.CurrentAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@RestController
@RequestMapping("/api/notifications/channels")
public class WebhookChannel {
    private final JdbcTemplate jdbc;
    private final CurrentAccount account;
    private final HttpClient client;
    private final URI endpoint;
    private final byte[] secret;

    public WebhookChannel(JdbcTemplate jdbc, CurrentAccount account,
            @Value("${TEAMWORK_NOTIFICATION_WEBHOOK_URL:}") String endpoint, @Value("${TEAMWORK_NOTIFICATION_WEBHOOK_SECRET:}") String secret) {
        this.jdbc = jdbc;
        this.account = account;
        this.endpoint = endpoint.isBlank() ? null : validateEndpoint(endpoint);
        this.secret = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (this.endpoint != null && this.secret.length < 32)
            throw new IllegalArgumentException("通知 Webhook 必须设置至少 32 字节的签名密钥");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    static URI validateEndpoint(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                uri.getUserInfo() != null || uri.getFragment() != null ||
                (uri.getPort() != -1 && uri.getPort() != 443))
            throw new IllegalArgumentException("通知 Webhook 必须使用标准 HTTPS 地址");
        return uri;
    }

    public record Preference(Boolean webhookEnabled) {}
    public record Channels(boolean webhookAvailable, boolean webhookEnabled) {}

    @GetMapping
    public Channels channels() {
        return new Channels(endpoint != null, enabled(account.requireMember().getAccountId()));
    }

    @PutMapping
    @Transactional
    public Channels configure(@RequestBody Preference preference) {
        long id = account.requireMember().getAccountId();
        if (preference.webhookEnabled() == null || (preference.webhookEnabled() && endpoint == null))
            throw new ApiException(400, "Webhook 未启用或参数无效");
        if (preference.webhookEnabled())
            jdbc.update("INSERT INTO notification_webhook_preference (account_id) VALUES (?) ON DUPLICATE KEY UPDATE account_id=account_id", id);
        else {
            jdbc.update("DELETE FROM notification_webhook_preference WHERE account_id=?", id);
            jdbc.update("UPDATE notification_webhook_delivery SET status='CANCELLED' WHERE account_id=? AND status='PENDING'", id);
        }
        return new Channels(endpoint != null, preference.webhookEnabled());
    }

    private boolean enabled(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification_webhook_preference WHERE account_id=?", Integer.class, id) != 0;
    }

    void afterNotification(long recipientId) {
        if (endpoint == null) return;
        jdbc.update("""
                INSERT INTO notification_webhook_delivery (id,account_id)
                SELECT ?,account_id FROM notification_webhook_preference WHERE account_id=?
                """, java.util.UUID.randomUUID().toString(), recipientId);
    }

    private record Delivery(String id, int attempts) {}

    @Scheduled(fixedDelay = 5000)
    public void drain() {
        if (endpoint == null) return;
        jdbc.update("UPDATE notification_webhook_delivery SET status='FAILED' WHERE status='PENDING' AND attempts>=5 AND next_attempt<=CURRENT_TIMESTAMP");
        for (Delivery delivery : jdbc.query("""
                SELECT d.id,d.attempts FROM notification_webhook_delivery d
                JOIN notification_webhook_preference p ON p.account_id=d.account_id
                WHERE d.status='PENDING' AND d.next_attempt<=CURRENT_TIMESTAMP AND d.attempts<5
                ORDER BY d.next_attempt,d.id LIMIT 10
                """, (r,i) -> new Delivery(r.getString(1),r.getInt(2)))) {
            int changed = jdbc.update("""
                    UPDATE notification_webhook_delivery SET attempts=attempts+1,
                        next_attempt=TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP)
                    WHERE id=? AND attempts=? AND status='PENDING' AND next_attempt<=CURRENT_TIMESTAMP
                    """, 30 << delivery.attempts(), delivery.id(), delivery.attempts());
            if (changed != 0 && send(delivery.id())) {
                jdbc.update("UPDATE notification_webhook_delivery SET status='DELIVERED' WHERE id=? AND attempts=? AND status='PENDING'",
                        delivery.id(), delivery.attempts() + 1);
            }
        }
    }

    protected boolean send(String id) {
        // Only the operator controls this destination; restrict it further with deployment egress rules.
        try {
            for (InetAddress address : InetAddress.getAllByName(endpoint.getHost())) {
                byte[] ip = address.getAddress();
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                        address.isSiteLocalAddress() || address.isMulticastAddress() || ip.length != 4 ||
                        (ip[0] & 255) == 0 || ((ip[0] & 255) == 100 && (ip[1] & 192) == 64)) return false;
            }
            String body = "{\"event\":\"teamwork.notification\",\"id\":\"" + id + "\"}";
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Teamwork-Signature", "sha256=" + signature(secret, body))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300;
        } catch (java.io.IOException | IllegalArgumentException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static String signature(byte[] secret, String body) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("无法签名 Webhook", ex);
        }
    }
}