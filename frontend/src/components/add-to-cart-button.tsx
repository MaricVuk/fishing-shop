"use client";

import { useEffect, useRef, useState } from "react";
import { Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useCart } from "@/hooks/use-cart";

type State = "idle" | "added" | "full";

export function AddToCartButton({ variantId }: { variantId: number }) {
  const { add } = useCart();
  const [state, setState] = useState<State>("idle");
  const timer = useRef<number | undefined>(undefined);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  function handleClick() {
    setState(add(variantId) ? "added" : "full");
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setState("idle"), 2000);
  }

  return (
    <>
      <Button variant="outline" size="lg" className="h-10" onClick={handleClick}>
        {state === "added" && <Check aria-hidden />}
        {state === "idle" && "Dodaj u korpu"}
        {state === "added" && "Dodato"}
        {state === "full" && "Korpa je puna"}
      </Button>
      <span role="status" className="sr-only">
        {state === "added" && "Dodato u korpu"}
        {state === "full" && "Korpa je puna"}
      </span>
    </>
  );
}
