"use client";

import Link from "next/link";
import { ShoppingCart } from "lucide-react";
import { useCart } from "@/hooks/use-cart";

export function CartLink() {
  const { quantity } = useCart();

  return (
    <Link
      href="/cart"
      className="flex min-h-10 items-center gap-2 rounded-lg px-3 text-sm font-medium transition-colors hover:bg-muted"
    >
      <ShoppingCart className="size-5" aria-hidden />
      <span>Korpa</span>
      {quantity > 0 && (
        <>
          <span
            aria-hidden
            className="min-w-5 rounded-full bg-primary px-1.5 text-center text-xs text-primary-foreground tabular-nums"
          >
            {quantity}
          </span>
          <span className="sr-only">Broj komada: {quantity}</span>
        </>
      )}
    </Link>
  );
}
