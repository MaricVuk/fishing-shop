import Link from "next/link";
import { ProductImage } from "@/components/product-image";
import { StockBadge } from "@/components/stock-badge";
import { formatPrice } from "@/lib/format";
import type { ProductSummary } from "@/lib/types";

export function ProductCard({ product }: { product: ProductSummary }) {
  return (
    <Link
      href={`/products/${product.id}`}
      className="group block h-full rounded-xl outline-offset-4"
    >
      <article className="flex h-full flex-col gap-3">
        <ProductImage
          src={product.imageUrl}
          alt={product.name}
          sizes="(min-width: 1280px) 20vw, (min-width: 640px) 30vw, 50vw"
        />
        <div className="flex flex-1 flex-col gap-1">
          {product.brand && (
            <p className="text-xs text-muted-foreground">{product.brand}</p>
          )}
          <h2 className="line-clamp-2 text-base leading-snug font-medium underline-offset-4 group-hover:underline">
            {product.name}
          </h2>
          <p className="mt-auto pt-1 text-base font-semibold tabular-nums">
            od {formatPrice(product.minPrice)}
          </p>
          <StockBadge inStock={product.inStock} />
        </div>
      </article>
    </Link>
  );
}
