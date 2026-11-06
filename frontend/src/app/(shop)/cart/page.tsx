import type { Metadata } from "next";
import { CartView } from "@/components/cart-view";

export const metadata: Metadata = {
  title: "Korpa",
  robots: { index: false },
};

export default function CartPage() {
  return (
    <div>
      <h1 className="text-3xl font-bold sm:text-4xl">Korpa</h1>
      <CartView />
    </div>
  );
}
