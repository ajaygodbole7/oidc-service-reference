import { Link } from "@tanstack/react-router";
// bundle-barrel-imports: import shadcn primitives directly from their files,
// never via a barrel, so the bundler can tree-shake unused exports.
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { formatInventoryStatus, formatMoney, type CatalogProduct } from "@/lib/commerce";

// Read-only product detail screen. Pure and presentational: the route
// (ProductRoute.tsx) reads the loaded product via useSuspenseQuery and passes
// it straight in, so this component only ever renders a loaded product. The
// loading skeleton and the not-found/error UI live at the route level
// (ProductRoute's pendingComponent / errorComponent), keeping this component a
// single loaded view — directly testable with a stub product.
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
          variant={product.inventoryStatus === "out_of_stock" ? "destructive" : "secondary"}
        >
          {formatInventoryStatus(product.inventoryStatus)}
        </Badge>
      </CardHeader>
      <Separator />
      <CardContent className="pt-6 text-sm leading-relaxed text-muted-foreground">
        {product.description}
      </CardContent>
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

export function ProductDetail({ product }: ProductDetailProps) {
  return (
    <div className="space-y-6">
      <BackToCatalog />
      <ProductBody product={product} />
    </div>
  );
}

export type { ProductDetailProps };
