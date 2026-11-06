import Image from "next/image";
import { Fish } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { formatPrice } from "@/lib/format";
import type { ProductSummary } from "@/lib/types";

export function ProductCard({ product }: { product: ProductSummary }) {
  return (
    <article className="flex h-full flex-col gap-3">
      <div className="relative aspect-[4/3] overflow-hidden rounded-xl bg-muted">
        {product.imageUrl ? (
          <Image
            src={product.imageUrl}
            alt={product.name}
            fill
            unoptimized
            sizes="(min-width: 1280px) 20vw, (min-width: 640px) 30vw, 50vw"
            className="object-cover"
          />
        ) : (
          <div className="flex h-full items-center justify-center text-muted-foreground/40">
            <Fish className="size-12" strokeWidth={1.25} aria-hidden />
          </div>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-1">
        {product.brand && (
          <p className="text-xs text-muted-foreground">{product.brand}</p>
        )}
        <h2 className="line-clamp-2 text-base leading-snug font-medium">
          {product.name}
        </h2>
        <p className="mt-auto pt-1 text-base font-semibold tabular-nums">
          od {formatPrice(product.minPrice)}
        </p>
        {product.inStock ? (
          <Badge variant="secondary" className="bg-accent text-accent-foreground">
            Na stanju
          </Badge>
        ) : (
          <Badge variant="outline" className="text-muted-foreground">
            Nema na stanju
          </Badge>
        )}
      </div>
    </article>
  );
}
