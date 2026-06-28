import { useActionState } from "react";
import { useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { createLazyRoute, type ErrorComponentProps } from "@tanstack/react-router";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import {
  createCatalogProduct,
  formatInventoryStatus,
  formatMoney,
  formatPriceString,
  updateCatalogProduct,
  type CatalogProduct,
  type CatalogProductDraft,
  type InventoryStatus
} from "@/lib/commerce";
import { catalogQueryOptions, queryKeys } from "@/lib/queries";

const INVENTORY_STATUSES: readonly InventoryStatus[] = [
  "IN_STOCK",
  "LOW_STOCK",
  "OUT_OF_STOCK"
];

function MerchantCatalogScreen() {
  const { data: products } = useSuspenseQuery(catalogQueryOptions());

  return (
    <section aria-labelledby="merchant-catalog-heading" className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 id="merchant-catalog-heading" className="text-xl font-semibold tracking-tight">
          Merchant catalog
        </h1>
        <p className="text-sm text-muted-foreground tabular-nums">
          {`${products.length} ${products.length === 1 ? "item" : "items"}`}
        </p>
      </header>

      <CreateProductForm />

      <Separator />

      <div className="flex flex-col gap-3" aria-label="Editable products">
        {products.map((product) => (
          <ProductEditForm key={product.id} product={product} />
        ))}
      </div>
    </section>
  );
}

function CreateProductForm() {
  const queryClient = useQueryClient();
  const [message, submitCreate, isCreating] = useActionState<string | null, FormData>(
    async (_prev, formData) => {
      try {
        const product = await createCatalogProduct(draftFromFormData(formData, true));
        await queryClient.invalidateQueries({ queryKey: queryKeys.catalog });
        await queryClient.invalidateQueries({ queryKey: queryKeys.product(product.id) });
        return `Created ${product.name}`;
      } catch (e) {
        return e instanceof Error ? e.message : "Could not create product";
      }
    },
    null
  );

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Create product</CardTitle>
      </CardHeader>
      <CardContent>
        <form action={submitCreate} className="grid gap-3 md:grid-cols-[1fr_1.5fr_9rem_12rem_auto]">
          <Field id="create-sku" label="SKU" name="sku" required disabled={isCreating} />
          <Field id="create-name" label="Name" name="name" required disabled={isCreating} />
          <Field
            id="create-price"
            label="Price"
            name="price"
            type="number"
            step="0.01"
            min="0"
            required
            disabled={isCreating}
          />
          <InventorySelect id="create-inventoryStatus" defaultValue="IN_STOCK" disabled={isCreating} />
          <Button type="submit" className="self-end" disabled={isCreating}>
            {isCreating ? "Creating..." : "Create"}
          </Button>
        </form>
        <ActionMessage message={message} />
      </CardContent>
    </Card>
  );
}

function ProductEditForm({ product }: { readonly product: CatalogProduct }) {
  const queryClient = useQueryClient();
  const [message, submitUpdate, isUpdating] = useActionState<string | null, FormData>(
    async (_prev, formData) => {
      try {
        const updated = await updateCatalogProduct(product.id, draftFromFormData(formData, false));
        queryClient.setQueryData(queryKeys.product(updated.id), updated);
        await queryClient.invalidateQueries({ queryKey: queryKeys.catalog });
        return `Saved ${updated.name}`;
      } catch (e) {
        return e instanceof Error ? e.message : "Could not update product";
      }
    },
    null
  );

  return (
    <Card>
      <CardContent className="pt-6">
        <form action={submitUpdate} className="grid gap-3 md:grid-cols-[1fr_9rem_12rem_auto]">
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <label htmlFor={`${product.id}-name`} className="text-sm font-medium">
                Name
              </label>
              <Badge variant="secondary">{product.sku ?? product.id}</Badge>
            </div>
            <input
              id={`${product.id}-name`}
              name="name"
              required
              defaultValue={product.name}
              disabled={isUpdating}
              className="flex h-9 w-full rounded-md border bg-background px-3 py-1 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50"
            />
          </div>
          <Field
            id={`${product.id}-price`}
            label="Price"
            name="price"
            type="number"
            step="0.01"
            min="0"
            required
            defaultValue={formatPriceString(product.priceCents)}
            disabled={isUpdating}
          />
          <InventorySelect
            id={`${product.id}-inventoryStatus`}
            defaultValue={product.inventoryStatus}
            disabled={isUpdating}
          />
          <div className="flex flex-col items-start justify-end gap-2">
            <span className="text-sm text-muted-foreground tabular-nums">
              {formatMoney(product.priceCents, product.currency)}
            </span>
            <Button type="submit" variant="outline" disabled={isUpdating}>
              {isUpdating ? "Saving..." : "Save"}
            </Button>
          </div>
        </form>
        <ActionMessage message={message} />
      </CardContent>
    </Card>
  );
}

function Field({
  id,
  label,
  name,
  type = "text",
  defaultValue,
  step,
  min,
  required = false,
  disabled = false
}: {
  readonly id: string;
  readonly label: string;
  readonly name: string;
  readonly type?: string;
  readonly defaultValue?: string;
  readonly step?: string;
  readonly min?: string;
  readonly required?: boolean;
  readonly disabled?: boolean;
}) {
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        name={name}
        type={type}
        defaultValue={defaultValue}
        step={step}
        min={min}
        required={required}
        disabled={disabled}
        className="flex h-9 w-full rounded-md border bg-background px-3 py-1 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50"
      />
    </div>
  );
}

function InventorySelect({
  id,
  defaultValue,
  disabled
}: {
  readonly id: string;
  readonly defaultValue: InventoryStatus;
  readonly disabled: boolean;
}) {
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="text-sm font-medium">
        Inventory
      </label>
      <select
        id={id}
        name="inventoryStatus"
        defaultValue={defaultValue}
        disabled={disabled}
        className="flex h-9 w-full rounded-md border bg-background px-3 py-1 text-sm shadow-xs outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:opacity-50"
      >
        {INVENTORY_STATUSES.map((status) => (
          <option key={status} value={status}>
            {formatInventoryStatus(status)}
          </option>
        ))}
      </select>
    </div>
  );
}

function ActionMessage({ message }: { readonly message: string | null }) {
  return message !== null ? (
    <p role="status" className="mt-3 text-sm text-muted-foreground">
      {message}
    </p>
  ) : null;
}

function draftFromFormData(formData: FormData, includeSku: boolean): CatalogProductDraft {
  const name = String(formData.get("name") ?? "").trim();
  const priceCents = parsePriceCents(String(formData.get("price") ?? ""));
  const inventoryStatus = String(formData.get("inventoryStatus") ?? "");
  if (!isInventoryStatus(inventoryStatus)) {
    throw new Error("Inventory status is invalid");
  }

  return {
    ...(includeSku ? { sku: String(formData.get("sku") ?? "").trim() } : {}),
    name,
    priceCents,
    inventoryStatus
  };
}

function parsePriceCents(value: string): number {
  const parsed = Number.parseFloat(value);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error("Price is invalid");
  }
  return Math.round(parsed * 100);
}

function isInventoryStatus(value: string): value is InventoryStatus {
  return INVENTORY_STATUSES.includes(value as InventoryStatus);
}

function MerchantPending() {
  return (
    <section aria-labelledby="merchant-catalog-heading" className="flex flex-col gap-4">
      <h1 id="merchant-catalog-heading" className="text-xl font-semibold tracking-tight">
        Merchant catalog
      </h1>
      <Card>
        <CardContent className="flex flex-col gap-3 pt-6">
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-40" />
        </CardContent>
      </Card>
    </section>
  );
}

function MerchantError({ error }: ErrorComponentProps) {
  return (
    <div
      role="alert"
      className="rounded-lg border border-destructive/40 bg-destructive/5 p-6 text-sm text-destructive"
    >
      <p className="font-medium">We could not load merchant catalog.</p>
      <p className="mt-1 text-destructive/80">
        {error instanceof Error ? error.message : "Please try again in a moment."}
      </p>
    </div>
  );
}

export const Route = createLazyRoute("/merchant/catalog")({
  component: MerchantCatalogScreen,
  pendingComponent: MerchantPending,
  errorComponent: MerchantError
});
