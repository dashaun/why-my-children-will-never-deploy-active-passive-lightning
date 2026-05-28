package dev.dashaun.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.CompletionContext;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.stereotype.Component;

/**
 * Logs every load-balancer pick — fires on each attempt, including retries.
 * When a region's container goes down, its health check drops it from the
 * supplier list and the next attempt rolls onto a different region.
 */
@Component
public class RegionSelectionLogger implements LoadBalancerLifecycle<Object, Object, ServiceInstance> {

    private static final Logger log = LoggerFactory.getLogger(RegionSelectionLogger.class);

    @Override
    public void onStart(Request<Object> request) {
        // no-op
    }

    @Override
    public void onStartRequest(Request<Object> request, Response<ServiceInstance> lbResponse) {
        if (lbResponse == null || !lbResponse.hasServer()) {
            log.warn("→ no instance available");
            return;
        }
        ServiceInstance instance = lbResponse.getServer();
        String region = instance.getMetadata().getOrDefault("region", instance.getInstanceId());
        log.info("→ attempting {} ({}:{})", region, instance.getHost(), instance.getPort());
    }

    @Override
    public void onComplete(CompletionContext<Object, ServiceInstance, Object> ctx) {
        if (ctx.getLoadBalancerResponse() == null || !ctx.getLoadBalancerResponse().hasServer()) {
            return;
        }
        ServiceInstance instance = ctx.getLoadBalancerResponse().getServer();
        String region = instance.getMetadata().getOrDefault("region", instance.getInstanceId());
        switch (ctx.status()) {
            case SUCCESS -> log.info("✓ wrote to {}", region);
            case FAILED -> log.warn("✗ failed at {} ({})",
                    region,
                    ctx.getThrowable() != null ? ctx.getThrowable().getClass().getSimpleName() : "?");
            case DISCARD -> log.warn("✗ discarded {}", region);
        }
    }
}
