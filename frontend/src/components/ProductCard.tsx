// bundle-barrel-imports: import shadcn primitives directly from their files,
// never via a barrel, so the bundler can tree-shake unused exports.
import type { ReactElement } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle
} from "@/components/ui/card";
import { QuickAddToCart } from "@/components/QuickAddToCart";
import { formatInventoryStatus, formatMoney, type CatalogProduct } from "@/lib/commerce";

type ProductCardProps = {
  readonly product: CatalogProduct;
  readonly detailsLink?: ReactElement;
  readonly quickAdd?: boolean;
};

// Leaf card for one catalog product. It stays router-free: callers pass an
// already-built details link, and can opt into the quick-add Action when the
// card appears in the catalog grid. The React Compiler auto-memoizes this, so
// a parent re-render doesn't re-render every card when its `product` is
// referentially stable — no manual React.memo needed.
export function ProductCard({ product, detailsLink, quickAdd = false }: ProductCardProps) {
  const initial = product.name.charAt(0);
  return (
    <Card className="flex h-full flex-col overflow-hidden">
      <div
        aria-hidden="true"
        className="flex aspect-video items-center justify-center bg-muted text-3xl font-semibold text-muted-foreground"
      >
        {product.imageUrl ? (
          <img alt="" className="size-full object-cover" src={product.imageUrl} />
        ) : (
          <span>{initial}</span>
        )}
      </div>
      <CardHeader>
        <div className="flex items-start justify-between gap-3">
          <CardTitle className="text-base">{product.name}</CardTitle>
          <span className="shrink-0 text-sm font-medium tabular-nums">
            {formatMoney(product.priceCents, product.currency)}
          </span>
        </div>
        {/* rendering-conditional-render: description is optional on the catalog
            API, so render it only when present — explicit ternary, never `&&`. */}
        {product.description !== undefined ? (
          <CardDescription className="line-clamp-2">{product.description}</CardDescription>
        ) : null}
      </CardHeader>
      <CardContent className="mt-auto">
        <Badge variant={product.inventoryStatus === "OUT_OF_STOCK" ? "destructive" : "secondary"}>
          {formatInventoryStatus(product.inventoryStatus)}
        </Badge>
      </CardContent>
      <CardFooter className="flex items-end justify-between gap-3">
        <span className="min-w-0 truncate text-xs text-muted-foreground">{product.id}</span>
        <div className="flex shrink-0 items-center gap-2">
          {detailsLink !== undefined ? (
            <Button asChild variant="outline" size="sm">
              {detailsLink}
            </Button>
          ) : null}
          {quickAdd ? <QuickAddToCart product={product} /> : null}
        </div>
      </CardFooter>
    </Card>
  );
}

export type { ProductCardProps };
