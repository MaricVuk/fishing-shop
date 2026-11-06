import { AddToCartButton } from "@/components/add-to-cart-button";
import { StockBadge } from "@/components/stock-badge";
import { describeAttributes, formatPrice } from "@/lib/format";
import type { VariantDetail } from "@/lib/types";

export function VariantList({ variants }: { variants: VariantDetail[] }) {
  return (
    <ul className="divide-y border-y">
      {variants.map((variant) => (
        <li
          key={variant.id}
          className="flex items-center justify-between gap-4 py-4"
        >
          <div className="min-w-0">
            <p className="font-medium">{describeAttributes(variant.attributes)}</p>
            <p className="mt-0.5 text-xs text-muted-foreground">
              Šifra: {variant.sku}
            </p>
          </div>
          <div className="flex shrink-0 flex-col items-end gap-1">
            <p className="font-semibold tabular-nums">
              {formatPrice(variant.price)}
            </p>
            <StockBadge inStock={variant.inStock} />
            {variant.inStock && (
              <div className="mt-2">
                <AddToCartButton variantId={variant.id} />
              </div>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}
