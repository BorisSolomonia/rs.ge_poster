FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci

FROM node:22-alpine AS builder
WORKDIR /app
ARG VITE_ROUTER_BASENAME=/camora
ARG VITE_API_PREFIX=/camora/api/v1
ARG VITE_API_BASE_URL=
ARG VITE_ASSET_BASE=
ENV VITE_ROUTER_BASENAME=$VITE_ROUTER_BASENAME
ENV VITE_API_PREFIX=$VITE_API_PREFIX
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_ASSET_BASE=$VITE_ASSET_BASE
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
RUN apk add --no-cache wget
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=30s \
    CMD wget -qO- http://localhost:3000/health || exit 1
