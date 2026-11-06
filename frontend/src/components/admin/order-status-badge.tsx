import { Badge } from "@/components/ui/badge";
import { statusLabel } from "@/lib/admin-orders";
import type { OrderStatus } from "@/lib/admin-types";

const VARIANTS: Record<OrderStatus, "default" | "secondary" | "outline" | "destructive"> = {
  NEW: "default",
  CONFIRMED: "secondary",
  SHIPPED: "secondary",
  DELIVERED: "outline",
  CANCELLED: "destructive",
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <Badge variant={VARIANTS[status]}>{statusLabel(status)}</Badge>;
}
