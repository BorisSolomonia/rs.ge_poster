# Camora ERP VM Deployment Guide

This deployment mirrors the `Order_app` VM pattern and is intended to run on the same VM and in the same GCP project.

## Architecture

- CI/CD: GitHub Actions
- Image registry: Artifact Registry repo `orderapp` in `us-central1`
- Runtime host: same VM as `Order_app`
- Runtime orchestrator: Docker Compose with shared external network `web`
- Public ingress: existing `Order_app` Caddy
- Public app path: `/camora`

## One-Time VM/Ingress Changes

Camora does not bind port `80`. It relies on `Order_app`'s Caddy to proxy:

- `/camora/api/*` -> `camora-erp-backend:8080`
- `/camora*` -> `camora-erp-frontend:3000`

Both apps must stay attached to the same external Docker network `web`.

## Required GitHub Secrets

Reuse the same secret names as `Order_app`:

- `GCP_PROJECT_ID`
- `GCP_SA_KEY`
- `VM_HOST`
- `VM_SSH_USER`
- `VM_SSH_KEY`

## Required Secret Manager Secret

Create:

- `camora-erp-env`

It should contain the production env file expected by `docker/compose.production.yml`.

Template is in [docker/.env.example](/C:/Users/Boris/Dell/Projects/APPS/Camora/camora_erp/docker/.env.example).

## Public URL

- `http://<VM_PUBLIC_IP>/camora`

## Notes

- Frontend is built with router basename `/camora`
- API requests are built to `/camora/api/v1`
- `Order_app` Caddy must strip `/camora` before proxying upstream
