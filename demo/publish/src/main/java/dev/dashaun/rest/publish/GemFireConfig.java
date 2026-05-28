package dev.dashaun.rest.publish;

import jakarta.annotation.PreDestroy;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GemFireConfig {

    private ClientCache cache;

    @Bean
    ClientCache clientCache(
            @Value("${gemfire.locator.host}") String host,
            @Value("${gemfire.locator.port}") int port,
            @Value("${region.name}") String regionName) {
        this.cache = new ClientCacheFactory()
                .addPoolLocator(host, port)
                .set("name", "publish-" + regionName)
                .set("log-level", "config")
                .create();
        return this.cache;
    }

    @Bean
    Region<String, Object> accountRegion(ClientCache cache) {
        return cache
                .<String, Object>createClientRegionFactory(ClientRegionShortcut.PROXY)
                .create("Account");
    }

    @PreDestroy
    void shutdown() {
        if (cache != null && !cache.isClosed()) {
            cache.close();
        }
    }
}
