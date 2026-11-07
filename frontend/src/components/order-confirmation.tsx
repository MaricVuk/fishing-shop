"use client";

import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useHydrated } from "@/hooks/use-hydrated";
import { useLastOrder } from "@/hooks/use-last-order";
import { formatPrice } from "@/lib/format";

export function OrderConfirmation() {
  const hydrated = useHydrated();
  const order = useLastOrder();

  if (!hydrated) {
    return <Skeleton className="h-10 w-72" aria-busy="true" />;
  }

  if (!order) {
    return (
      <div className="flex flex-col items-start gap-3 rounded-xl bg-muted px-6 py-10">
        <h1 className="text-3xl font-bold">Nema nedavne porudžbine</h1>
        <p className="max-w-prose text-muted-foreground">
          Ovde se prikazuje potvrda posle slanja porudžbine.
        </p>
        <Link href="/products" className={buttonVariants({ size: "lg" })}>
          Pogledaj proizvode
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-xl">
      <h1 className="text-3xl font-bold sm:text-4xl">Porudžbina je primljena</h1>
      <p className="mt-3 text-lg">
        Hvala! Broj vaše porudžbine je <strong className="tabular-nums">{order.number}</strong>.
      </p>
      <p className="mt-1 text-muted-foreground">
        Javićemo vam se u vezi sa isporukom.
      </p>
      <ul className="mt-8 divide-y border-y">
        {order.items.map((item) => (
          <li key={item.sku} className="flex justify-between gap-4 py-3 text-sm">
            <div className="min-w-0">
              <p className="font-medium">{item.productName}</p>
              <p className="text-muted-foreground">
                Šifra: {item.sku} × {item.quantity}
              </p>
            </div>
            <p className="shrink-0 tabular-nums">{formatPrice(item.lineTotal)}</p>
          </li>
        ))}
      </ul>
      <p className="mt-4 flex justify-between text-base font-semibold">
        <span>Ukupno</span>
        <span className="tabular-nums">{formatPrice(order.total)}</span>
      </p>
      <Link href="/products" className={`${buttonVariants({ size: "lg" })} mt-8`}>
        Nastavi kupovinu
      </Link>
    </div>
  );
}
