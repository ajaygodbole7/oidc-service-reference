/// <reference types="vitest" />

import path from "node:path";
import babel from "@rolldown/plugin-babel";
import tailwindcss from "@tailwindcss/vite";
import react, { reactCompilerPreset } from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import type { ProxyOptions } from "vite";

// The browser is at http://127.0.0.1:5173. In the Frame B split-BFF
// architecture there are TWO upstreams instead of one:
//   - /auth/* → APISIX (http://127.0.0.1:9080)
//   - /api/** → APISIX (http://127.0.0.1:9080)
//
// In a full `docker compose up` run, APISIX is the actual ingress and
// path-routes both /auth/* and /api/** itself. The Vite dev proxy is the
// dev-loop equivalent of that ingress: it splits the two prefixes here
// so that the inner loop (frontend dev) can talk to the same browser-facing
// ingress used by the Compose stack, without the SPA knowing that Auth
// Service is internal-only.
//
// Both proxies use the same forwarded-header treatment as before:
//   - changeOrigin is false so upstreams receive Host: 127.0.0.1:5173
//   - X-Forwarded-* tell Spring's ForwardedHeaderFilter
//     (server.forward-headers-strategy=framework) to compute baseUrl as
//     the SPA origin, so the OAuth redirect_uri matches the URI
//     registered in Keycloak (http://127.0.0.1:5173/auth/callback/idp).
//   - The OAuth callback lands at 127.0.0.1:5173 (Vite), is proxied to
//     Auth Service, and the Set-Cookie response flows back through Vite.
//     The browser binds the cookie to origin 5173, so subsequent
//     /api/** and /auth/me requests carry it.
const authTarget = process.env.VITE_AUTH_TARGET ?? "http://127.0.0.1:9080";
const apiTarget = process.env.VITE_API_TARGET ?? "http://127.0.0.1:9080";

const forwardedHeaderProxy = (target: string): ProxyOptions => ({
  target,
  changeOrigin: false,
  configure: (proxy) => {
    proxy.on("proxyReq", (proxyReq, req) => {
      const host = req.headers.host ?? "127.0.0.1:5173";
      proxyReq.setHeader("X-Forwarded-Host", host);
      proxyReq.setHeader("X-Forwarded-Proto", "http");
      proxyReq.setHeader("X-Forwarded-Port", "5173");
    });
  }
});

const authProxy: ProxyOptions = forwardedHeaderProxy(authTarget);
const apiProxy: ProxyOptions = forwardedHeaderProxy(apiTarget);

// We intentionally do NOT set Content-Security-Policy from the dev server.
// Vite's HMR client needs runtime code generation that any sensible CSP
// would block, so a "dev CSP" is either toothless (allow everything) or
// breaks the inner loop. Production CSP is owned by the Auth Service /
// APISIX response headers and the production web server — that's where
// it belongs and where it can be nonce-based without HMR getting in the
// way.
//
// Referrer-Policy and X-Content-Type-Options are uncontroversial in dev
// and useful for parity with production behavior.
const devSecurityHeaders = {
  "Referrer-Policy": "strict-origin-when-cross-origin",
  "X-Content-Type-Options": "nosniff"
};

export default defineConfig({
  plugins: [
    react(),
    // React Compiler: auto-memoizes components/hooks at build time, so manual
    // React.memo / useMemo / useCallback for referential stability are no longer
    // needed (and have been removed from the FE components). The compiler memoizes
    // automatically.
    //
    // plugin-react v6 transforms with oxc (not Babel) and no longer takes a
    // `babel` option; per its README the React Compiler is wired as a separate
    // @rolldown/plugin-babel running reactCompilerPreset(). target "19" makes the
    // preset use react/compiler-runtime — which React 19 ships built in — so NO
    // separate react-compiler-runtime package is required.
    babel({ presets: [reactCompilerPreset({ target: "19" })] }),
    tailwindcss()
  ],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src")
    }
  },
  server: {
    host: "127.0.0.1",
    port: 5173,
    headers: devSecurityHeaders,
    proxy: {
      "/auth": authProxy,
      "/api": apiProxy
    }
  },
  build: {
    target: "es2022",
    sourcemap: true,
    reportCompressedSize: false,
    rollupOptions: {
      output: {
        // Split the framework runtime out of the app entry chunk. Keeps the
        // main `index` entry small (and under the tight per-entry bundle
        // budget) while the lazy route chunks code-split per screen. Total
        // bytes are unchanged — this is purely how they're grouped.
        manualChunks: (id: string): string | undefined => {
          if (id.includes("/node_modules/react") || id.includes("/node_modules/scheduler")) {
            return "react-vendor";
          }
          if (id.includes("/node_modules/@tanstack/")) {
            return "tanstack";
          }
          return undefined;
        }
      }
    }
  },
  test: {
    environment: "jsdom",
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["./src/test-setup.ts"]
  }
});
