# Repository Guidelines

## Project Structure & Module Organization

This repository has two main applications:

- `backend/`: Spring Boot 3 API, reconciliation engine, SOAP integrations, and persistence logic.
- `frontend/`: React 18 + Vite + TypeScript UI.
- `docker/`: production compose files and env templates.
- `.github/workflows/`: CI and VM deployment pipelines..

Backend code lives in `backend/src/main/java/ge/camora/erp`, with config in `backend/src/main/resources` and tests in `backend/src/test/java`. Frontend code lives in `frontend/src`; use `pages/` for routes, `components/` for shared UI, and `api/` for HTTP clients.

## Build, Test, and Development Commands

Run from the repository root unless noted:

- `npm run dev`: starts the Vite frontend on `http://localhost:5173`.
- `npm run dev:backend`: runs the Spring Boot backend.
- `npm run build`: builds the frontend production bundle.
- `npm run lint`: runs frontend ESLint.
- `mvn -f backend/pom.xml verify -B`: runs backend tests.
- `mvn -f backend/pom.xml -DskipTests compile`: quick backend compile check.
- `docker compose -f docker/compose.production.yml config`: validates production compose inputs.

For local full-stack work, run frontend and backend in separate terminals.

## Coding Style & Naming Conventions

Use 2-space indentation in `frontend/` and 4 spaces in `backend/`. Follow existing naming:

- `PascalCase` for React components and Java classes
- `camelCase` for functions, variables, and hooks
- `UPPER_SNAKE_CASE` for environment variables

Keep Java packages under `ge.camora.erp.*`. Prefer small, focused React components and Spring services that map cleanly to one responsibility.

## Testing Guidelines

Backend tests use Spring Boot Test and should mirror production packages under `backend/src/test/java`. Name test classes `*Test`. For frontend changes, there is no dedicated test runner yet, so require `npm run lint` and `npm run build` before opening a PR.

## Commit & Pull Request Guidelines

Git history is not available in this workspace snapshot, so use short imperative commits such as `Add purchase reconciliation endpoint` or `Fix supplier mapping reuse`. PRs should include a clear summary, affected routes or APIs, env or deployment notes, and screenshots for UI changes.

## Security & Configuration Tips

Do not hardcode secrets. Keep rs.ge credentials and deployment values in environment variables or GCP Secret Manager. Production traffic is served under `/camora`, so frontend base-path and proxy changes must stay compatible with that deployment path.
