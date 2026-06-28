import { useActionState } from "react";
import { Link, Outlet } from "@tanstack/react-router";
import { loginHref, signOut } from "@/auth";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { useCart, useMe } from "@/lib/queries";

// The app shell: brand + nav + the sign-in/sign-out control, then the routed
// screen via <Outlet/>. The auth control reuses auth.ts exactly as the old
// App.tsx did — loginHref() for the anonymous Sign in link, signOut() for the
// authenticated Sign out button. No token state lives here; useMe() reads the
// browser-safe /auth/me projection.
export function AppShell() {
  const me = useMe();
  const user = me.data ?? null;

  // The shared cart badge. useCart is gated on an authenticated session (never
  // fires for a guest); its count is the sum of line quantities. The badge
  // updates OPTIMISTICALLY on add because ProductDetail's Action writes the
  // cart cache via setQueryData(cartQueryOptions(), …) — this read sees that
  // write instantly, then the post-add invalidate reconciles it.
  const cart = useCart(user != null);
  const itemCount = (cart.data?.items ?? []).reduce((sum, item) => sum + item.quantity, 0);

  // React 19 form Action for sign-out. signOut() stays in auth.ts (no token
  // logic moves into the view); the Action just calls it, and React's built-in
  // pending flag from useActionState drives the disabled/label state instead of
  // hand-rolled state. signOut() ends in a top-level navigation, so the Action
  // returns no state — null is the initial and only state.
  const [, submitSignOut, isSigningOut] = useActionState<null>(async () => {
    await signOut();
    return null;
  }, null);

  return (
    <div className="min-h-svh bg-background text-foreground">
      <header className="border-b">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-6 px-6 py-4">
          <div className="flex items-center gap-6">
            <Link to="/" className="flex flex-col leading-tight">
              <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                oidc-service-reference
              </span>
              <span className="text-lg font-semibold">Commerce</span>
            </Link>
            <nav className="flex items-center gap-1" aria-label="Primary">
              <Button asChild variant="ghost" size="sm">
                <Link to="/" activeProps={{ "data-active": "true" }}>
                  Catalog
                </Link>
              </Button>
              <Button asChild variant="ghost" size="sm">
                <Link to="/cart" data-testid="cart-nav" activeProps={{ "data-active": "true" }}>
                  {/* rendering-conditional-render: show the count only when the
                      cart has items — explicit ternary, never `&&`. */}
                  {itemCount > 0 ? `Cart (${itemCount})` : "Cart"}
                </Link>
              </Button>
              <Button asChild variant="ghost" size="sm">
                <Link to="/orders" activeProps={{ "data-active": "true" }}>
                  Orders
                </Link>
              </Button>
              <Button asChild variant="ghost" size="sm">
                <Link to="/merchant/catalog" activeProps={{ "data-active": "true" }}>
                  Merchant
                </Link>
              </Button>
            </nav>
          </div>

          {me.isPending ? (
            <span className="text-sm text-muted-foreground">Checking session…</span>
          ) : user ? (
            <div className="flex items-center gap-3">
              <span className="text-sm text-muted-foreground">
                {user.preferred_username ?? user.sub}
              </span>
              <form action={submitSignOut}>
                <Button type="submit" variant="outline" size="sm" disabled={isSigningOut}>
                  {isSigningOut ? "Signing out…" : "Sign out"}
                </Button>
              </form>
            </div>
          ) : (
            <Button asChild size="sm">
              <a href={loginHref()} data-testid="sign-in-link">
                Sign in
              </a>
            </Button>
          )}
        </div>
      </header>

      <Separator />

      <main className="mx-auto max-w-5xl px-6 py-10">
        <Outlet />
      </main>
    </div>
  );
}
