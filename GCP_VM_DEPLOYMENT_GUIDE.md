# Camora ERP VM Deployment Guide

This deployment now uses a shared-proxy VM layout on the same VM and in the same GCP project.

## Architecture.

- CI/CD: GitHub Actions
- Image registry: Artifact Registry repo `orderapp` in `us-central1`
- Runtime host: same VM as `Order_app`
- Runtime orchestrator: Docker Compose with shared external network `web`
- Public ingress: dedicated shared Caddy stack
- Public app path: `/camora`

## Runtime Layout.

- `shared-proxy` stack
  - `shared-caddy`
- `orderapp` stack
  - `orderapp-backend`
  - `orderapp-frontend`
- `camora-erp` stack
  - `camora-erp-backend`
  - `camora-erp-frontend`

Camora does not bind port `80`. The shared proxy routes:

- `/camora/api/*` -> `camora-erp-backend:8080`
- `/camora*` -> `camora-erp-frontend:3000`

Order App routes:

- `/api/*` -> `orderapp-backend:8080`
- `/actuator/*` -> `orderapp-backend:8080`
- all other paths -> `orderapp-frontend:3000`

All stacks must stay attached to the same external Docker network `web`.

## Required GitHub Secrets

Reuse the same secret names as `Order_app`:

- `GCP_PROJECT_ID`
- `GCP_SA_KEY`
- `VM_HOST`
- `VM_SSH_USER`
- `VM_SSH_KEY`

The shared proxy workflow only needs:

- `VM_HOST`
- `VM_SSH_USER`
- `VM_SSH_KEY`

## Required Secret Manager Secret

Create:

- `camora-erp-env`

It should contain the production env file expected by `docker/compose.production.yml`.

Template is in [docker/.env.example](/C:/Users/Boris/Dell/Projects/APPS/Camora/camora_erp/docker/.env.example).

## Deployment Order

1. Deploy the shared proxy with `.github/workflows/deploy-proxy.yml`
2. Deploy `Order_app`
3. Deploy `Camora`

The proxy files live in:

- [proxy/compose.production.yml](/C:/Users/Boris/Dell/Projects/APPS/Camora/camora_erp/proxy/compose.production.yml)
- [proxy/Caddyfile.production](/C:/Users/Boris/Dell/Projects/APPS/Camora/camora_erp/proxy/Caddyfile.production)

## Public URL

- `http://<VM_PUBLIC_IP>/camora`

## Notes

- Frontend is built with router basename `/camora`
- API requests are built to `/camora/api/v1`
- The proxy handles routing for both apps; app deploys no longer own Caddy
