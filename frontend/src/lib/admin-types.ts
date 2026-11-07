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
  number: string;
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
  number: string;
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
  creditNote: InvoiceSummary;
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

export type JobStatus = "PENDING" | "DONE" | "DEAD" | "RESOLVED";

export type JobType =
  | "ORDER_CONFIRMATION_EMAIL"
  | "ORDER_INVOICE"
  | "ORDER_INVOICE_EMAIL"
  | "ORDER_CREDIT_NOTE"
  | "ORDER_CREDIT_NOTE_EMAIL";

export type AdminJob = {
  id: number;
  type: JobType;
  orderId: number;
  status: JobStatus;
  attempts: number;
  nextAttemptAt: string;
  lastError: string | null;
  createdAt: string;
  finishedAt: string | null;
};

export type AdminJobSummary = { counts: Record<JobStatus, number>; oldestOverdueSince: string | null };

export type AdminJobOverview = AdminJobSummary & { jobs: AdminPage<AdminJob> };

export type ReportGranularity = "DAY" | "WEEK" | "MONTH";

export type SalesBucket = {
  start: string;
  revenue: string;
  orders: number;
  newRevenue: string;
  newOrders: number;
};

export type SalesReport = {
  from: string;
  to: string;
  granularity: ReportGranularity;
  totals: { revenue: string; orders: number; newRevenue: string; newOrders: number };
  buckets: SalesBucket[];
};

export type BestSeller = {
  variantId: number;
  productId: number;
  productName: string;
  sku: string;
  attributes: Record<string, string>;
  active: boolean;
  quantity: number;
  revenue: string;
  orders: number;
};

export type BestSellerReport = { from: string; to: string; items: BestSeller[] };

export type LowStockItem = {
  variantId: number;
  productId: number;
  productName: string;
  sku: string;
  attributes: Record<string, string>;
  quantity: number;
};

export type LowStockReport = { threshold: number; total: number; items: LowStockItem[] };

export type Dashboard = {
  pendingOrders?: { count: number; oldestSince?: string };
  sales?: SalesReport;
  lowStock?: { threshold: number; total: number };
};
