# Data Agent User Frontend

Lightweight Nuxt user application for data Q&A and model configuration. It uses
the same Spring Boot backend as the management frontend.

## Development

```bash
pnpm install
pnpm dev
```

The development server runs on `http://localhost:3000` and proxies API requests
to `http://localhost:8065`.

To call a remote backend directly, set:

```bash
NUXT_PUBLIC_API_BASE_URL=http://your-backend:8065
```

The management frontend can be started independently on port 3001:

```bash
cd ../data-agent-frontend-nuxt
node node_modules/nuxt/bin/nuxt.mjs dev --port 3001
```
