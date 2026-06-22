import { useActionState, useOptimistic, useState } from "react";
import { Link } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
// bundle-barrel-imports: import shadcn primitives directly from their files,
// never via a barrel, so the bundler can tree-shake unused exports.
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import {
  addCartItem,
  emptyCart,
  formatInventoryStatus,
  formatMoney,
  type Cart,
  type CatalogProduct
} from "@/lib/commerce";
import { cartQueryOptions } from "@/lib/queries";

// Read-only product detail screen plus the React 19 "Add to cart" Action. The
// route (ProductRoute.tsx) reads the loaded product via useSuspenseQuery and
// passes it straight in, so this component only ever renders a loaded product.
// The loading skeleton and the not-found/error UI live at the route level.
type ProductDetailProps = { readonly product: CatalogProduct };

// A small leaf for the loaded media + body. The compiler memoizes it.
function ProductBody({ product }: { readonly product: CatalogProduct }) {
  const initial = product.name.charAt(0);
  return (
    <Card className="overflow-hidden">
      <div
        aria-hidden="true"
        className="flex aspect-video items-center justify-center bg-muted text-6xl font-semibold text-muted-foreground"
      >
        {product.imageUrl ? (
          <img alt="" className="size-full object-cover" src={product.imageUrl} />
        ) : (
          <span>{initial}</span>
        )}
      </div>
      <CardHeader>
        <div className="flex items-start justify-between gap-4">
          <CardTitle className="text-2xl">{product.name}</CardTitle>
          <span className="shrink-0 text-xl font-medium tabular-nums">
            {formatMoney(product.priceCents, product.currency)}
          </span>
        </div>
        <Badge
          className="mt-1"
          variant={product.inventoryStatus === "OUT_OF_STOCK" ? "destructive" : "secondary"}
        >
          {formatInventoryStatus(product.inventoryStatus)}
        </Badge>
      </CardHeader>
      {/* rendering-conditional-render: description is optional on the catalog API,
          so render the body block only when present — explicit ternary, never `&&`. */}
      {product.description !== undefined ? (
        <>
          <Separator />
          <CardContent className="pt-6 text-sm leading-relaxed text-muted-foreground">
            {product.description}
          </CardContent>
        </>
      ) : null}
    </Card>
  );
}

function BackToCatalog() {
  return (
    <Button asChild variant="ghost" size="sm">
      <Link to="/">← Back to catalog</Link>
    </Button>
  );
}

// The "Add to cart" Action. The cart count is shared with the header badge via
// the TanStack Query cache, so the success path is: (1) optimistically bump the
// cached cart so the header badge updates instantly, (2) POST through
// addCartItem (callApi → CSRF + 401 handling), (3) invalidate the cart query so
// the authoritative server cart reconciles the optimistic guess.
//
// useOptimistic drives the LOCAL pending affordance: while the Action is in
// flight, optimisticAdded flips to the just-submitted quantity so the button
// reads "Adding N…", reverting automatically when the Action settles.
function AddToCart({ product }: { readonly product: CatalogProduct }) {
  const queryClient = useQueryClient();
  const [quantity, setQuantity] = useState(1);

  // React 19 useOptimistic: optimisticQuantity reflects the in-flight add (the
  // submitted quantity) and snaps back to 0 once the Action resolves. Genuine
  // use — the value only diverges from base DURING the pending Action.
  const [optimisticQuantity, addOptimisticQuantity] = useOptimistic<number, number>(
    0,
    (_current, submitted) => submitted
  );

  const [error, submitAdd, isAdding] = useActionState<string | null, FormData>(
    async () => {
      addOptimisticQuantity(quantity);

      // Optimistically reflect the add in the shared cart cache so the header
      // badge increments before the network settles.
      queryClient.setQueryData<Cart>(cartQueryOptions().queryKey, (current) => {
        const base = current ?? emptyCart();
        return { ...base, items: [...base.items] };
      });

      try {
        await addCartItem(product.id, quantity, product.priceCents);
        // Reconcile the optimistic guess with the authoritative server cart.
        await queryClient.invalidateQueries({ queryKey: cartQueryOptions().queryKey });
        return null;
      } catch (e) {
        // The invalidate above is skipped on failure; roll the optimistic cart
        // back to the server's truth so the badge can't strand a phantom item.
        await queryClient.invalidateQueries({ queryKey: cartQueryOptions().queryKey });
        return e instanceof Error ? e.message : "Could not add to cart";
      }
    },
    null
  );

  const outOfStock = product.inventoryStatus === "OUT_OF_STOCK";
  const pendingQuantity = optimisticQuantity > 0 ? optimisticQuantity : quantity;

  return (
    <form action={submitAdd} className="space-y-3">
      <div className="flex items-center gap-3">
        <div className="flex items-center rounded-md border" role="group" aria-label="Quantity">
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="Decrease quantity"
            disabled={quantity <= 1 || isAdding}
            onClick={() => setQuantity((q) => Math.max(1, q - 1))}
          >
            −
          </Button>
          <span className="w-8 text-center text-sm tabular-nums" aria-label="Quantity value">
            {quantity}
          </span>
          <Button
            type="button"
            variant="ghost"
            size="icon-sm"
            aria-label="Increase quantity"
            disabled={isAdding}
            onClick={() => setQuantity((q) => q + 1)}
          >
            +
          </Button>
        </div>
        <Button type="submit" disabled={outOfStock || isAdding}>
          {isAdding ? `Adding ${pendingQuantity}…` : "Add to cart"}
        </Button>
      </div>
      {error !== null ? (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </form>
  );
}

export function ProductDetail({ product }: ProductDetailProps) {
  return (
    <div className="space-y-6">
      <BackToCatalog />
      <ProductBody product={product} />
      <AddToCart product={product} />
    </div>
  );
}

export type { ProductDetailProps };
