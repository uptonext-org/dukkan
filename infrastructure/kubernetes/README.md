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

Merchant photos (B-32) default to the filesystem `MediaStorage` under `/var/lib/dukkan/media`,
mounted as an `emptyDir` (2 GiB limit). That is **not production-grade**: files disappear when a pod is
rescheduled, and with two replicas each pod only serves the images it stored itself.

For production use the S3 store (B-32a) with S3, Cloudflare R2 or another S3-compatible service:

```bash
# ConfigMap (non-secret):
DUKKAN_MEDIA_STORAGE=s3
DUKKAN_MEDIA_S3_BUCKET=dukkan-media
DUKKAN_MEDIA_S3_REGION=eu-central-1
DUKKAN_MEDIA_S3_ENDPOINT=            # empty for AWS; https://<account>.r2.cloudflarestorage.com for R2
DUKKAN_MEDIA_PUBLIC_BASE_URL=https://media.dukkan.app   # CDN or public bucket domain in front of the bucket
# Secret, or nothing at all with IRSA / workload identity (AWS default credentials chain):
DUKKAN_MEDIA_S3_ACCESS_KEY=... DUKKAN_MEDIA_S3_SECRET_KEY=...
```

Objects are written once under `ab/<sha256>.<ext>` with `Cache-Control: public, max-age=31536000,
immutable`. Make the bucket (or the CDN origin) publicly readable for that prefix; the backend never
needs delete rights. The `emptyDir` can then be removed from the deployment.
