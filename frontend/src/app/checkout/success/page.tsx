import type { Metadata } from "next";
import { OrderConfirmation } from "@/components/order-confirmation";

export const metadata: Metadata = {
  title: "Porudžbina primljena",
  robots: { index: false },
};

export default function CheckoutSuccessPage() {
  return <OrderConfirmation />;
}
