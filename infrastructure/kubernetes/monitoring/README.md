# Monitoring (optional, Prometheus Operator)

Apply after installing the Prometheus Operator (kube-prometheus-stack):

```bash
kubectl apply -n dukkan -f infrastructure/kubernetes/monitoring/
```

- `servicemonitor.yaml`: scrapes `/actuator/prometheus` on the management port (8081) through the
  headless `dukkan-backend-management` Service. Without the operator, the pod annotations
  (`prometheus.io/*`) in `backend-deployment.yaml` work with plain Prometheus discovery.
- `grafana-dashboard-configmap.yaml`: the "Dukkan Backend" dashboard for the Grafana sidecar
  (label `grafana_dashboard: "1"`); the source is `dashboards/dukkan-backend.json` (also importable
  in Grafana by hand). Panels: orders, status changes, API latency p50/p95/p99, requests by status,
  slowest endpoints, outbox backlog, language-model calls/latency/tokens, JVM heap, DB connections.
- `prometheus-rules.yaml`: alerts on a stuck outbox, failing language-model calls, server errors
  and slow requests.

Metrics of note (besides JVM, HikariCP and `http_server_requests_seconds`):

| Metric | Meaning |
|---|---|
| `dukkan_orders_placed_total{fulfilment}` | Orders placed (DELIVERY, PICKUP) |
| `dukkan_orders_status_changes_total{status,by}` | Status changes by customer or merchant |
| `dukkan_outbox_pending`, `dukkan_outbox_retrying` | Unpublished events; those that already failed |
| `dukkan_outbox_oldest_pending_seconds` | Age of the oldest unpublished event |
| `dukkan_ai_requests_seconds{feature,outcome}` | Language-model calls (capture, translation): count and latency |
| `dukkan_ai_tokens_total{feature,direction}` | Tokens used, for cost tracking |
