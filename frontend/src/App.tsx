import { useEffect, useMemo, useState } from "react";
import { fetchMe, loginHref, signOut, type User } from "./auth";

type LoadState = "loading" | "anonymous" | "authenticated";

export function App() {
  const [state, setState] = useState<LoadState>("loading");
  const [user, setUser] = useState<User | null>(null);
  // Captures the route the user was on when login became required so
  // the callback can replay it. Computed once on mount: loginHref()
  // reads window.location, which is stable for this SPA (no client-side
  // router), and the anonymous panel only renders for state="anonymous"
  // — by then the URL has not changed since mount.
  const signInHref = useMemo(() => loginHref(), []);

  useEffect(() => {
    const controller = new AbortController();
    let alive = true;
    fetchMe(controller.signal)
      .then((u) => {
        if (!alive) return;
        if (u) {
          setUser(u);
          setState("authenticated");
        } else {
          setState("anonymous");
        }
      })
      .catch(() => {
        if (alive) setState("anonymous");
      });
    return () => {
      alive = false;
      controller.abort();
    };
  }, []);

  return (
    <main className="app-shell">
      <section className="auth-panel">
        <p className="eyebrow">oidc-service-reference</p>
        <h1>Commerce Shell</h1>

        {state === "loading" && <p>Loading…</p>}

        {state === "anonymous" && (
          <>
            <p>Browse the catalog soon. Sign in to start a BFF session.</p>
            <a href={signInHref} data-testid="sign-in-link">
              Sign in
            </a>
          </>
        )}

        {state === "authenticated" && user && (
          <>
            <p>
              Signed in as <strong>{user.preferred_username ?? user.sub}</strong>
            </p>
            <p>Roles: {(user.roles ?? []).join(", ") || "(none)"}</p>
            <form
              onSubmit={(event) => {
                event.preventDefault();
                void signOut();
              }}
            >
              <button type="submit">Sign out</button>
            </form>
          </>
        )}
      </section>
    </main>
  );
}
