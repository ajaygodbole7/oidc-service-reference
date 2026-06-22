import { useSuspenseQuery } from "@tanstack/react-query";
import { createLazyRoute, Link, type ErrorComponentProps } from "@tanstack/react-router";
import type { ReactNode } from "react";
import { CatalogGrid } from "@/components/CatalogGrid";
import { Card } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { catalogQueryOptions } from "@/lib/queries";

// The storefront landing. The route loader (src/router.tsx) prefetches the
// catalog into the Query cache via ensureQueryData(catalogQueryOptions()), so
// by the time this component renders the data is already present —
// useSuspenseQuery reads the same cache entry and never returns undefined.
// Loading is handled by pendingComponent, fetch failures by errorComponent;
// CatalogGrid stays a pure presentational view of the loaded products.
//
// We supply the typed TanStack `Link` so cells navigate to
// /products/$productId with param-encoded ids; the grid itself stays
// router-free and unit-testable.
function renderProductLink(productId: string, children: ReactNode): ReactNode {
  return (
    <Link
      to="/products/$productId"
      params={{ productId }}
      className="block h-full rounded-xl outline-none transition-shadow hover:shadow-md focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
    >
      {children}
    </Link>
  );
}

function CatalogScreen() {
  const { data: products } = useSuspenseQuery(catalogQueryOptions());
  return <CatalogGrid products={products} renderLink={renderProductLink} />;
}

const GRID_CLASS = "grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4";
const CELL_VISIBILITY = "[content-visibility:auto] [contain-intrinsic-size:auto_22rem]";
const SKELETON_KEYS = ["s1", "s2", "s3", "s4", "s5", "s6"] as const;

// Route-level loading state, shown while the loader's prefetch is in flight.
function CatalogPending() {
  return (
    <section aria-labelledby="catalog-heading" className="space-y-4">
      <header className="flex items-baseline justify-between gap-4">
        <h1 id="catalog-heading" className="text-xl font-semibold tracking-tight">
          Featured products
        </h1>
      </header>
      <Separator />
      <ul aria-hidden="true" className={GRID_CLASS}>
        {SKELETON_KEYS.map((key) => (
          <li key={key} className={CELL_VISIBILITY}>
            <Card className="flex h-full flex-col overflow-hidden">
              <Skeleton className="aspect-video w-full rounded-none" />
              <div className="space-y-3 p-6">
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-3 w-full" />
                <Skeleton className="h-3 w-5/6" />
                <Skeleton className="h-5 w-20" />
              </div>
            </Card>
          </li>
        ))}
      </ul>
    </section>
  );
}

// Route-level error state, shown when the loader / query throws (e.g. the BFF
// catalog read fails). Fail-closed: an unloadable catalog surfaces the alert,
// never a blank or partial grid.
function CatalogError({ error }: ErrorComponentProps) {
  return (
    <div
      role="alert"
      className="rounded-lg border border-destructive/40 bg-destructive/5 p-6 text-sm text-destructive"
    >
      <p className="font-medium">We could not load the catalog.</p>
      <p className="mt-1 text-destructive/80">
        {error instanceof Error ? error.message : "Please try again in a moment."}
      </p>
    </div>
  );
}

export const Route = createLazyRoute("/")({
  component: CatalogScreen,
  pendingComponent: CatalogPending,
  errorComponent: CatalogError
});
