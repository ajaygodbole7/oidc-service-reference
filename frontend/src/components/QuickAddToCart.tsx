import { useActionState, useOptimistic } from "react";
import { ShoppingCart } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { addCartItem, emptyCart, type Cart, type CatalogProduct } from "@/lib/commerce";
import { cartQueryOptions } from "@/lib/queries";

type QuickAddToCartProps = {
  readonly product: CatalogProduct;
};

export function QuickAddToCart({ product }: QuickAddToCartProps) {
  const queryClient = useQueryClient();
  const [optimisticAdding, setOptimisticAdding] = useOptimistic(false, (_current, next: boolean) => next);

  const [error, submitAdd, isAdding] = useActionState<string | null>(async () => {
    setOptimisticAdding(true);
    queryClient.setQueryData<Cart>(cartQueryOptions().queryKey, (current) => {
      const base = current ?? emptyCart();
      const items = base.items.some((item) => item.productId === product.id)
        ? base.items.map((item) =>
            item.productId === product.id
              ? {
                  ...item,
                  quantity: item.quantity + 1,
                  lineTotalCents: item.lineTotalCents + product.priceCents
                }
              : item
          )
        : [
            ...base.items,
            {
              id: product.id,
              productId: product.id,
              name: product.name,
              quantity: 1,
              unitPriceCents: product.priceCents,
              lineTotalCents: product.priceCents
            }
          ];
      const subtotalCents = items.reduce((sum, item) => sum + item.lineTotalCents, 0);
      return {
        ...base,
        items,
        subtotalCents,
        totalCents: subtotalCents + base.estimatedTaxCents
      };
    });

    try {
      await addCartItem(product.id, 1, product.priceCents);
      await queryClient.invalidateQueries({ queryKey: cartQueryOptions().queryKey });
      return null;
    } catch (e) {
      await queryClient.invalidateQueries({ queryKey: cartQueryOptions().queryKey });
      return e instanceof Error ? e.message : "Could not add to cart";
    }
  }, null);

  const busy = isAdding || optimisticAdding;
  const outOfStock = product.inventoryStatus === "OUT_OF_STOCK";

  return (
    <form action={submitAdd} className="flex flex-col gap-2">
      <Button type="submit" size="sm" disabled={busy || outOfStock}>
        <ShoppingCart data-icon="inline-start" />
        {busy ? "Adding..." : "Add"}
      </Button>
      {error !== null ? (
        <p role="alert" className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </form>
  );
}
