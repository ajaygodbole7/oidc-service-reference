// bundle-barrel-imports: import shadcn primitives directly from their files,
// never via a barrel, so the bundler can tree-shake unused exports.
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle
} from "@/components/ui/card";
import { formatInventoryStatus, formatMoney, type CatalogProduct } from "@/lib/commerce";

type ProductCardProps = {
  readonly product: CatalogProduct;
};

// Leaf presentational component for one catalog product. Reused by the catalog
// grid and the product detail screen. The React Compiler auto-memoizes this, so
// a parent re-render doesn't re-render every card when its `product` is
// referentially stable — no manual React.memo needed.
export function ProductCard({ product }: ProductCardProps) {
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
        <CardDescription className="line-clamp-2">{product.description}</CardDescription>
      </CardHeader>
      <CardContent className="mt-auto">
        <Badge variant={product.inventoryStatus === "out_of_stock" ? "destructive" : "secondary"}>
          {formatInventoryStatus(product.inventoryStatus)}
        </Badge>
      </CardContent>
      <CardFooter className="text-xs text-muted-foreground">{product.id}</CardFooter>
    </Card>
  );
}
