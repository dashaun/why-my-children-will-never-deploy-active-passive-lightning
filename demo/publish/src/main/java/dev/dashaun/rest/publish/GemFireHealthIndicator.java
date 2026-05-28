package dev.dashaun.rest.publish;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Forces a round-trip to the local GemFire server. If the server / locator is
 * unreachable, the call throws and Actuator returns 503 — which removes this
 * instance from the gateway's load-balancer pool.
 */
@Component
public class GemFireHealthIndicator implements HealthIndicator {

    private final ClientCache cache;
    private final Region<String, Object> account;

    public GemFireHealthIndicator(ClientCache cache, Region<String, Object> account) {
        this.cache = cache;
        this.account = account;
    }

    @Override
    public Health health() {
        if (cache.isClosed()) {
            return Health.down().withDetail("cache", "closed").build();
        }
        try {
            account.sizeOnServer();
            return Health.up()
                    .withDetail("region", account.getName())
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("region", account.getName())
                    .build();
        }
    }
}
