"use client";

import { useEffect, useRef, useState } from "react";
import { Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useCart } from "@/hooks/use-cart";
import { MAX_QUANTITY, type AddStatus } from "@/lib/cart";

const LABELS: Record<AddStatus, string> = {
  added: "Dodato",
  "cart-full": "Korpa je puna",
  "quantity-limit": `Najviše ${MAX_QUANTITY} komada`,
};

export function AddToCartButton({ variantId }: { variantId: number }) {
  const { add } = useCart();
  const [status, setStatus] = useState<AddStatus | null>(null);
  const timer = useRef<number | undefined>(undefined);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  function handleClick() {
    setStatus(add(variantId));
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setStatus(null), 2000);
  }

  return (
    <>
      <Button variant="outline" size="lg" className="h-10" onClick={handleClick}>
        {status === "added" && <Check aria-hidden />}
        {status === null ? "Dodaj u korpu" : LABELS[status]}
      </Button>
      <span role="status" className="sr-only">
        {status !== null && LABELS[status]}
      </span>
    </>
  );
}
