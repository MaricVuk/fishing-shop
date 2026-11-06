export type EmployeeRole = "OWNER" | "SELLER" | "WAREHOUSE";

export type Employee = {
  id: number;
  email: string;
  fullName: string;
  role: EmployeeRole;
};

export type AdminPage<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type StockRow = {
  variantId: number;
  productId: number;
  productName: string;
  brand: string | null;
  sku: string;
  attributes: Record<string, string>;
  price: number;
  active: boolean;
  quantity: number;
  reserved: number;
};

export type StockMovementType = "IN" | "OUT" | "ADJUSTMENT" | "RETURN";

export type StockMovement = {
  id: number;
  type: StockMovementType;
  quantityChange: number;
  quantityAfter: number;
  orderId: number | null;
  reason: string | null;
  createdAt: string;
};

export type StockLevel = { variantId: number; quantity: number };

export type OrderStatus = "NEW" | "CONFIRMED" | "SHIPPED" | "DELIVERED" | "CANCELLED";

export type AdminOrderRow = {
  id: number;
  createdAt: string;
  status: OrderStatus;
  customerName: string;
  customerEmail: string;
  total: number;
  itemCount: number;
};

export type AuditEntry = {
  id: number;
  actorEmail: string;
  action: string;
  entityType: string;
  entityId: string;
  oldValue: Record<string, unknown> | null;
  newValue: Record<string, unknown> | null;
  createdAt: string;
};

export type AdminOrderDetail = {
  id: number;
  status: OrderStatus;
  createdAt: string;
  total: number;
  customer: { name: string; email: string; phone: string | null; shippingAddress: string };
  items: {
    variantId: number;
    productName: string;
    sku: string;
    unitPrice: number;
    quantity: number;
    lineTotal: number;
  }[];
  allowedStatuses: OrderStatus[];
  invoice: InvoiceSummary;
  history: AuditEntry[];
};

export type InvoiceStatus = "NONE" | "PENDING" | "ISSUED" | "FAILED";

export type InvoiceSummary = { status: InvoiceStatus; number: string | null; issuedAt: string | null };

export type AdminEmployee = {
  id: number;
  email: string;
  fullName: string;
  role: EmployeeRole;
  active: boolean;
  createdAt: string;
};
