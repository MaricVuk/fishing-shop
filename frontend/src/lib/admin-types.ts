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
};

export type StockMovementType = "IN" | "OUT" | "ADJUSTMENT";

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
