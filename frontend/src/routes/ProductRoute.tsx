import { useSuspenseQuery } from "@tanstack/react-query";
import { createLazyRoute, Link, useParams } from "@tanstack/react-router";
import { ProductDetail } from "@/components/ProductDetail";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { productQueryOptions } from "@/lib/queries";

// Product detail route. The loader (src/router.tsx) prefetches the product into
// the Query cache via ensureQueryData(productQueryOptions(productId)); this
// component then reads the same cache entry with useSuspenseQuery, so the
// product is guaranteed present. Loading is handled by pendingComponent.
//
// fetchProduct throws on non-OK (incl. 404), so a missing product makes the
// loader / query reject → the route's errorComponent renders the
// "not found / unavailable" state. Fail-closed: a missing or unloadable
// product never renders a partial body.
function ProductScreen() {
  const { productId } = useParams({ from: "/products/$productId" });
  const { data: product } = useSuspenseQuery(productQueryOptions(productId));
  return <ProductDetail product={product} />;
}

// The back link is available in every route state (loading / error / loaded),
// matching the prior behaviour where it survived each branch.
function BackToCatalog() {
  return (
    <Button asChild variant="ghost" size="sm">
      <Link to="/">← Back to catalog</Link>
    </Button>
  );
}

function ProductPending() {
  return (
    <div className="space-y-6">
      <BackToCatalog />
      <Card className="overflow-hidden">
        <Skeleton className="aspect-video w-full rounded-none" />
        <CardHeader>
          <div className="flex items-start justify-between gap-4">
            <Skeleton className="h-7 w-1/2" />
            <Skeleton className="h-7 w-20" />
          </div>
          <Skeleton className="h-5 w-24" />
        </CardHeader>
        <Separator />
        <CardContent className="space-y-2 pt-6">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-5/6" />
          <Skeleton className="h-4 w-2/3" />
        </CardContent>
      </Card>
    </div>
  );
}

// errorComponent for the route; for the catalog read a 404 (or any fetch
// failure) lands here as the not-found / unavailable state. We don't surface
// the raw error message — the not-found copy is the intended UX for a missing
// product.
function ProductError() {
  return (
    <div className="space-y-6">
      <BackToCatalog />
      <Card role="alert">
        <CardHeader>
          <CardTitle className="text-lg">Product not found</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          This product could not be found or is currently unavailable.
        </CardContent>
      </Card>
    </div>
  );
}

export const Route = createLazyRoute("/products/$productId")({
  component: ProductScreen,
  pendingComponent: ProductPending,
  errorComponent: ProductError
});
