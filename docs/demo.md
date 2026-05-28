<!-- .slide: data-background-color="#191e1e" -->

# The Demo

## Five Regions. One App. Zero Standby.

---

## The Stack

| Layer | Technology |
|-------|-----------|
| App | Spring Boot 3.5 + Spring Cloud Gateway |
| Runtime | Java 25 |
| Data Grid | VMware GemFire 10.1 |
| Replication | GemFire WAN Gateways |
| Observability | Actuator + Prometheus + Grafana |

> All in this repo, under `demo/`

---

## Five Clusters, Five Time Zones

```text
publish1 → gf1 — New York       (distributed-system-id=1)
publish2 → gf2 — Amsterdam      (distributed-system-id=2)
publish3 → gf3 — Kolkata        (distributed-system-id=3)
publish4 → gf4 — São Paulo      (distributed-system-id=4)
publish5 → gf5 — Tokyo          (distributed-system-id=5)
```

Every cluster is a peer. Every region serves writes.

---

## The Region (the data kind)

```bash
gfsh -e "connect --locator=gf1-locator[10001]" \
     -e "create region --name=Account \
                       --type=PARTITION \
                       --gateway-sender-id=\
                         Account_Sender_to_2,\
                         Account_Sender_to_3,\
                         Account_Sender_to_4,\
                         Account_Sender_to_5"
```

One `Account` region. Five clusters. Writes anywhere, read everywhere.

---

## WAN Gateways — Parallel & Persistent

```bash
create gateway-sender \
  --id=Account_Sender_to_2 \
  --parallel=true \
  --remote-distributed-system-id=2 \
  --enable-persistence=true \
  --enable-batch-conflation=true
```

- **`parallel=true`** — every server ships its own slice
- **`enable-persistence=true`** — survive a sender restart
- **`batch-conflation=true`** — only the latest update per key crosses the WAN

---

## The Publish App — one per region

```java
@PostMapping("/publish")
public Map<String, Object> publish() {
    var key = UUID.randomUUID().toString();
    var msg = Map.of(
        "region",    regionName,
        "timezone",  zoneId.getId(),
        "localTime", LocalDateTime.now(zoneId).toString()
    );
    account.put(key, msg);     // → WAN → 4 other regions
    return Map.of("key", key, "message", msg);
}
```

---

## The Health Check — local GemFire only

```java
@Override
public Health health() {
    try {
        account.sizeOnServer();           // round-trip to local cluster
        return Health.up().build();
    } catch (Exception e) {
        return Health.down(e).build();    // → 503 → LB drops me
    }
}
```

If `publish3` can't reach its own GemFire, the gateway stops sending it traffic.

---

## The Gateway — one endpoint, five backends

```yaml
spring.cloud:
  loadbalancer:
    configurations: health-check
    health-check:
      path: { publish: /actuator/health }
      interval: 5s
  gateway.server.webflux.routes:
    - id: publish
      uri: lb://publish
      predicates: [ Path=/publish ]
      filters:
        - name: Retry
          args: { retries: 4, series: SERVER_ERROR,
                  exceptions: [ java.io.IOException ] }
```

---

## Per-Attempt Logging

```java
public void onStartRequest(Request<?> req, Response<ServiceInstance> r) {
    var region = r.getServer().getMetadata().get("region");
    log.info("→ attempting {}", region);
}
public void onComplete(CompletionContext<?, ServiceInstance, ?> ctx) {
    var region = ctx.getLoadBalancerResponse().getServer()
                    .getMetadata().get("region");
    if (ctx.getStatus() == SUCCESS) log.info("✓ wrote to {}", region);
    else                            log.warn("✗ failed at {}", region);
}
```

Every retry attempt fires through here — including the recovery one.

---

## Bring It Up

```bash
cd demo
./mvnw -DskipTests package spring-boot:build-image      # publish:0 + gateway:0

docker compose -f docker/region1/compose.yaml   up -d   # gf1 + publish1
docker compose -f docker/region2/compose.yaml   up -d
docker compose -f docker/region3/compose.yaml   up -d
docker compose -f docker/region4/compose.yaml   up -d
docker compose -f docker/region5/compose.yaml   up -d
docker compose -f docker/monitoring/compose.yaml up -d
docker compose -f docker/gateway/compose.yaml   up -d
```

---

## Drive It

```bash
$ while true; do
    curl -s -X POST http://localhost:8080/publish | jq -c .message
    sleep 1
  done
{"region":"region1","timezone":"America/New_York", ... }
{"region":"region2","timezone":"Europe/Amsterdam", ... }
{"region":"region3","timezone":"Asia/Kolkata",     ... }
...
```

```bash
$ docker logs -f gateway
→ attempting region1   ✓ wrote to region1
→ attempting region2   ✓ wrote to region2
→ attempting region3   ✓ wrote to region3
```

---

## Now Kill One

```bash
$ docker compose -f docker/region3/compose.yaml down
```

```text
→ attempting region3   ✗ failed at region3 (ConnectException)
→ attempting region4   ✓ wrote to region4     ← retry
→ attempting region5   ✓ wrote to region5
→ attempting region1   ✓ wrote to region1
→ attempting region2   ✓ wrote to region2
# region3 never appears again
```

Notes:
- The health check (5s interval) marks region3 DOWN; LB removes it from rotation
- In-flight failures retry against another region — no 5xx leaks to the caller
- This is the game-day exercise you said you never run
