import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  RouterProvider,
  createMemoryHistory
} from "@tanstack/react-router";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createAppRouter } from "./router";

// Smoke test for the foundation: the shell renders, nav is present, and the
// auth control reflects /auth/me. The old monolithic App.test.tsx (catalog +
// cart rendering) is retired here — the screen agents own per-screen rendering
// tests once they fill the route bodies. This proves the router + QueryClient +
// AppShell wiring and the no-token-in-storage invariant.
function setupFetch(routes: Record<string, () => Response | Promise<Response>>) {
  return vi.spyOn(global, "fetch").mockImplementation(async (input) => {
    const url =
      typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    const handler = routes[url];
    if (!handler) throw new Error(`unhandled fetch: ${url}`);
    return handler();
  });
}

function renderApp(initialPath: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  });
  const router = createAppRouter(queryClient);
  // A fresh memory history per render keeps tests isolated from window.location.
  Object.assign(router.options, {
    history: createMemoryHistory({ initialEntries: [initialPath] })
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}

describe("AppShell + router foundation", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("renders the shell brand and primary nav", async () => {
    setupFetch({ "/auth/me": () => new Response(null, { status: 401 }) });

    renderApp("/");

    expect(await screen.findByText("Commerce")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Catalog" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Cart" })).toBeInTheDocument();
  });

  it("shows the sign-in entry for an anonymous session and stores no tokens", async () => {
    setupFetch({ "/auth/me": () => new Response(null, { status: 401 }) });

    renderApp("/");

    const signIn = await screen.findByTestId("sign-in-link");
    expect(signIn).toHaveAttribute("href", `/auth/login?return_to=${encodeURIComponent("/")}`);
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it("shows the signed-in identity and sign-out for an authenticated session", async () => {
    setupFetch({
      "/auth/me": () => Response.json({ sub: "alice-123", preferred_username: "alice", roles: ["user"] })
    });

    renderApp("/");

    expect(await screen.findByText("alice")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /sign out/i })).toBeInTheDocument();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it("renders the lazy cart route placeholder", async () => {
    setupFetch({ "/auth/me": () => new Response(null, { status: 401 }) });

    renderApp("/cart");

    expect(await screen.findByText(/Cart/)).toBeInTheDocument();
  });
});
