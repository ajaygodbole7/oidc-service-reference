import type { ReactNode } from "react";
// bundle-barrel-imports: import each shadcn primitive directly from its file so
// the bundler can tree-shake — never via a barrel.
import { ProductCard } from "@/components/ProductCard";
import { Separator } from "@/components/ui/separator";
import type { CatalogProduct } from "@/lib/commerce";

// Presentational catalog grid. Pure: it receives the loaded products + a link
// renderer as props, so it renders identically with or without a router. The
// loading skeleton and error UI now live at the route level (CatalogRoute's
// pendingComponent / errorComponent), so this component only ever renders the
// loaded list (which may be empty). The link itself is injected as a render
// prop: the route passes the typed TanStack `Link` (to="/products/$productId"
// params={{ productId }}); tests pass a plain anchor. Keeping the generic Link
// out of this file's types avoids coupling the grid to the router's generics.
export type CatalogGridProps = {
  readonly products: readonly CatalogProduct[];
  readonly renderLink: (productId: string, children: ReactNode) => ReactNode;
};

// rendering-content-visibility: each off-screen cell skips layout/paint until it
// scrolls near the viewport. contain-intrinsic-size reserves a stable box so the
// scrollbar doesn't jump as the catalog grows.
const CELL_VISIBILITY = "[content-visibility:auto] [contain-intrinsic-size:auto_22rem]";

const GRID_CLASS = "grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4";

function EmptyState() {
  return (
    <div className="rounded-lg border border-dashed p-12 text-center">
      <p className="text-base font-medium">No products yet</p>
      <p className="mt-1 text-sm text-muted-foreground">
        Check back soon — the catalog is being stocked.
      </p>
    </div>
  );
}

function ProductGrid({
  products,
  renderLink
}: {
  readonly products: readonly CatalogProduct[];
  readonly renderLink: (productId: string, children: ReactNode) => ReactNode;
}) {
  return (
    <ul aria-label="Products" className={GRID_CLASS}>
      {products.map((product) => (
        <li key={product.id} className={CELL_VISIBILITY}>
          {/* The compiler memoizes ProductCard; `product` comes referentially
              stable from the query cache, so passing it directly keeps cells
              from re-rendering. */}
          <ProductCard
            product={product}
            detailsLink={renderLink(product.id, "View")}
            quickAdd={true}
          />
        </li>
      ))}
    </ul>
  );
}

export function CatalogGrid({ products, renderLink }: CatalogGridProps) {
  const itemCount = products.length;
  return (
    <section aria-labelledby="catalog-heading" className="space-y-4">
      <header className="flex items-baseline justify-between gap-4">
        <h1 id="catalog-heading" className="text-xl font-semibold tracking-tight">
          Featured products
        </h1>
        <p className="text-sm text-muted-foreground tabular-nums">
          {`${itemCount} ${itemCount === 1 ? "item" : "items"}`}
        </p>
      </header>
      <Separator />
      {/* rendering-conditional-render: explicit ternary, never `&&`, so a falsy
          state never leaks a stray `0`/empty render. */}
      {itemCount === 0 ? <EmptyState /> : <ProductGrid products={products} renderLink={renderLink} />}
    </section>
  );
}
