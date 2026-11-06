import { useSyncExternalStore } from "react";
import { cartQuantity } from "@/lib/cart";
import {
  addVariantToCart,
  clearCart,
  getCartSnapshot,
  getServerCartSnapshot,
  removeVariantFromCart,
  setVariantQuantity,
  subscribeToCart,
} from "@/lib/cart-store";

export function useCart() {
  const lines = useSyncExternalStore(
    subscribeToCart,
    getCartSnapshot,
    getServerCartSnapshot,
  );

  return {
    lines,
    quantity: cartQuantity(lines),
    add: addVariantToCart,
    setQuantity: setVariantQuantity,
    remove: removeVariantFromCart,
    clear: clearCart,
  };
}
