import { useSyncExternalStore } from "react";
import {
  getLastOrderSnapshot,
  getServerLastOrderSnapshot,
  subscribeToLastOrder,
} from "@/lib/last-order";

export function useLastOrder() {
  return useSyncExternalStore(
    subscribeToLastOrder,
    getLastOrderSnapshot,
    getServerLastOrderSnapshot,
  );
}
