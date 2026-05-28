package dev.dashaun.rest.publish;

import org.apache.geode.cache.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

@RestController
public class PublishController {

    private static final Logger log = LoggerFactory.getLogger(PublishController.class);

    private final Region<String, Object> account;
    private final String regionName;
    private final ZoneId zoneId;

    public PublishController(
            Region<String, Object> account,
            @Value("${region.name}") String regionName,
            @Value("${region.timezone}") String timezone) {
        this.account = account;
        this.regionName = regionName;
        this.zoneId = ZoneId.of(timezone);
    }

    @PostMapping("/publish")
    public Map<String, Object> publish() {
        String key = UUID.randomUUID().toString();
        Map<String, Object> message = Map.of(
                "region", regionName,
                "timezone", zoneId.getId(),
                "localTime", LocalDateTime.now(zoneId).toString(),
                "instant", Instant.now().toString()
        );
        account.put(key, message);
        log.info("published key={} from region={}", key, regionName);
        return Map.of("key", key, "message", message);
    }
}
