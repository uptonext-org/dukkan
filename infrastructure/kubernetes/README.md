# Kubernetes manifests (target: Kubernetes 1.36.5)

The MVP runs locally with Docker Compose. These manifests are a minimal starting
point for one shared staging cluster and are **not** a production setup. Before
production you still need managed PostgreSQL, an ingress/TLS setup, secret
management (for example External Secrets) and resource tuning.

```bash
kubectl apply -k infrastructure/kubernetes
# Create the secret out of band; never commit it:
kubectl -n dukkan create secret generic dukkan-backend \
  --from-literal=DUKKAN_DB_PASSWORD=... --from-literal=DUKKAN_JWT_SECRET=...
# Optional: AI product capture with Claude (ADR 0011). Without it the rule-based parser answers.
#   --from-literal=DUKKAN_ANTHROPIC_API_KEY=...
```

Helm is not used: one service with static configuration does not need templating.

## Uploaded images

Merchant photos (B-32) are stored by the backend's filesystem `MediaStorage` under
`/var/lib/dukkan/media`, mounted as an `emptyDir` (2 GiB limit). That is **not production-grade**:
the files disappear when a pod is rescheduled, and with two replicas each pod only serves the images
it stored itself. Before production, switch to S3-compatible object storage (backlog follow-up of
B-32) or mount a ReadWriteMany PersistentVolumeClaim, and set `DUKKAN_MEDIA_PUBLIC_BASE_URL` in the
ConfigMap to the public ingress/CDN prefix (for example `https://api.dukkan.app/api/v1/media`).
