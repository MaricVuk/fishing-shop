"use client";

import Link from "next/link";
import { Notice } from "@/components/notice";
import { ProductImage } from "@/components/product-image";
import { QuantityStepper } from "@/components/quantity-stepper";
import { Button, buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useCart } from "@/hooks/use-cart";
import { useHydrated } from "@/hooks/use-hydrated";
import { useVariantLines } from "@/hooks/use-variant-lines";
import { buildCartView, type CartItem } from "@/lib/cart";
import { describeAttributes, formatMinor, formatPrice } from "@/lib/format";
import { cn } from "@/lib/utils";

function CartSkeleton() {
  return (
    <div className="mt-8 flex flex-col gap-6" aria-busy="true" aria-label="Učitavanje korpe">
      {Array.from({ length: 2 }, (_, index) => (
        <div key={index} className="flex gap-4">
          <Skeleton className="aspect-[4/3] w-24 rounded-xl" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-5 w-2/3" />
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="mt-2 h-10 w-32" />
          </div>
        </div>
      ))}
    </div>
  );
}

function CartLineRow({
  item,
  onQuantityChange,
  onRemove,
}: {
  item: CartItem;
  onQuantityChange: (quantity: number) => void;
  onRemove: () => void;
}) {
  const { line, variant } = item;

  return (
    <li className="flex gap-4 py-5">
      <ProductImage
        src={variant?.imageUrl ?? null}
        alt={variant?.productName ?? ""}
        sizes="96px"
        className="w-24 shrink-0 self-start"
      />
      <div className="min-w-0 flex-1">
        {variant ? (
          <>
            <Link
              href={`/products/${variant.productId}`}
              className="font-medium underline-offset-4 hover:underline"
            >
              {variant.productName}
            </Link>
            <p className="text-sm text-muted-foreground">
              {describeAttributes(variant.attributes)}
            </p>
            <p className="text-xs text-muted-foreground">Šifra: {variant.sku}</p>
            {!variant.inStock && (
              <p className="mt-1 text-sm text-destructive">
                Trenutno nema na stanju. Uklonite stavku da biste nastavili.
              </p>
            )}
          </>
        ) : (
          <p className="text-destructive">
            Ovaj proizvod više nije dostupan. Uklonite stavku da biste nastavili.
          </p>
        )}
        <div className="mt-3 flex flex-wrap items-center gap-3">
          {variant && (
            <QuantityStepper
              value={line.quantity}
              onChange={onQuantityChange}
              label={`Količina: ${variant.productName}`}
            />
          )}
          <Button variant="ghost" className="h-10" onClick={onRemove}>
            Ukloni
          </Button>
        </div>
      </div>
      {variant && (
        <div className="text-right">
          <p className="font-semibold tabular-nums">{formatMinor(item.lineTotalMinor)}</p>
          {line.quantity > 1 && (
            <p className="text-xs text-muted-foreground tabular-nums">
              {formatPrice(variant.price)} po komadu
            </p>
          )}
        </div>
      )}
    </li>
  );
}

export function CartView() {
  const hydrated = useHydrated();
  const cart = useCart();
  const { status, lines: variantLines, retry } = useVariantLines(
    cart.lines.map((line) => line.variantId),
  );

  if (!hydrated) return <CartSkeleton />;

  if (cart.lines.length === 0) {
    return (
      <Notice title="Korpa je prazna" description="Dodajte proizvode da biste nastavili.">
        <Link href="/products" className={buttonVariants({ size: "lg" })}>
          Pogledaj proizvode
        </Link>
      </Notice>
    );
  }

  if (status === "loading") return <CartSkeleton />;

  if (status === "error") {
    return (
      <Notice
        title="Korpa trenutno nije dostupna"
        description="Nismo uspeli da učitamo cene i dostupnost. Pokušajte ponovo za trenutak."
      >
        <Button size="lg" onClick={retry}>
          Pokušaj ponovo
        </Button>
      </Notice>
    );
  }

  const view = buildCartView(cart.lines, variantLines);

  return (
    <div className="mt-6 grid gap-8 lg:grid-cols-[1fr_22rem] lg:gap-12">
      <ul className="divide-y border-y">
        {view.items.map((item) => (
          <CartLineRow
            key={item.line.variantId}
            item={item}
            onQuantityChange={(quantity) =>
              cart.setQuantity(item.line.variantId, quantity)
            }
            onRemove={() => cart.remove(item.line.variantId)}
          />
        ))}
      </ul>
      <aside className="h-fit rounded-xl bg-muted p-6 lg:sticky lg:top-6">
        <h2 className="text-lg font-semibold">Pregled</h2>
        <dl className="mt-4 flex flex-col gap-2 text-sm">
          <div className="flex justify-between">
            <dt className="text-muted-foreground">Broj komada</dt>
            <dd className="tabular-nums">{view.quantity}</dd>
          </div>
          <div className="flex justify-between text-base font-semibold">
            <dt>Ukupno</dt>
            <dd className="tabular-nums" aria-live="polite">
              {formatMinor(view.totalMinor)}
            </dd>
          </div>
        </dl>
        {view.hasProblems ? (
          <>
            <p className="mt-4 text-sm text-destructive">
              Uklonite nedostupne stavke da biste nastavili.
            </p>
            <Button size="lg" className="mt-4 h-11 w-full" disabled>
              Nastavi na porudžbinu
            </Button>
          </>
        ) : (
          <Link
            href="/checkout"
            className={cn(buttonVariants({ size: "lg" }), "mt-6 h-11 w-full")}
          >
            Nastavi na porudžbinu
          </Link>
        )}
      </aside>
    </div>
  );
}
