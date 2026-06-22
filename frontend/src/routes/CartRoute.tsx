import { createLazyRoute } from "@tanstack/react-router";
import { useCart, useMe } from "@/lib/queries";
import { CartView } from "@/components/CartView";

// The cart screen route. It derives auth state from useMe() and gates the cart
// query on it: useCart(authenticated) NEVER fires for a guest. All rendering
// lives in the pure CartView (testable without the router); this component only
// projects the two TanStack Query results into CartView's props. No token state
// — identity comes from /auth/me, cart from /api/cart, both via the data layer.
//
// rendering-conditional-render: the auth flag is an explicit boolean, and while
// the session itself is still resolving we report a "pending" cart so the user
// sees the loading skeleton rather than a sign-in flash that flips on settle.
function CartScreen() {
  const me = useMe();
  const authenticated = me.data != null;

  const cart = useCart(authenticated);

  const sessionResolving = me.isPending;
  const status = sessionResolving || cart.isPending ? "pending" : cart.isError ? "error" : "success";

  return (
    <CartView
      authenticated={authenticated || sessionResolving}
      cart={cart.data}
      status={status}
      error={cart.error}
    />
  );
}

export const Route = createLazyRoute("/cart")({
  component: CartScreen
});
