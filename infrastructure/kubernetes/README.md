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
```

Helm is not used: one service with static configuration does not need templating.
