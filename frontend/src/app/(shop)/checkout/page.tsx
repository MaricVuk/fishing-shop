import type { Metadata } from "next";
import { CheckoutForm } from "@/components/checkout-form";

export const metadata: Metadata = {
  title: "Završetak porudžbine",
  robots: { index: false },
};

export default function CheckoutPage() {
  return (
    <div>
      <h1 className="text-3xl font-bold sm:text-4xl">Završetak porudžbine</h1>
      <CheckoutForm />
    </div>
  );
}
