import type { CartLine } from "@/lib/cart";
import { describeAttributes } from "@/lib/format";
import type { FieldViolation, OrderRequest, VariantLine } from "@/lib/types";

export type CheckoutValues = {
  name: string;
  email: string;
  phone: string;
  address: string;
};

export type CheckoutField = keyof CheckoutValues;

export type FieldErrors = Partial<Record<CheckoutField, string>>;

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^[0-9+(][0-9+()/ .-]{4,48}[0-9)]$/;

const LIMITS: Record<CheckoutField, number> = {
  name: 150,
  email: 200,
  phone: 50,
  address: 500,
};

const REQUIRED: Record<CheckoutField, string> = {
  name: "Unesite ime i prezime",
  email: "Unesite adresu e-pošte",
  phone: "Unesite broj telefona",
  address: "Unesite adresu za isporuku",
};

const INVALID: Partial<Record<CheckoutField, string>> = {
  email: "Unesite ispravnu adresu e-pošte",
  phone: "Unesite ispravan broj telefona",
};

const TOO_LONG = "Uneti tekst je predugačak";

const SERVER_FIELDS: Record<string, CheckoutField> = {
  "customer.name": "name",
  "customer.email": "email",
  "customer.phone": "phone",
  "customer.shippingAddress": "address",
};

export function validateCheckout(values: CheckoutValues): FieldErrors {
  const errors: FieldErrors = {};

  (Object.keys(LIMITS) as CheckoutField[]).forEach((field) => {
    const value = values[field].trim();

    if (value === "") {
      if (field !== "phone") errors[field] = REQUIRED[field];
      return;
    }
    if (value.length > LIMITS[field]) {
      errors[field] = TOO_LONG;
      return;
    }
    if (field === "email" && !EMAIL.test(value)) errors[field] = INVALID[field];
    if (field === "phone" && !PHONE.test(value)) errors[field] = INVALID[field];
  });

  return errors;
}

export function mapServerErrors(violations: FieldViolation[]): FieldErrors {
  const errors: FieldErrors = {};

  for (const { field, code } of violations) {
    const mapped = SERVER_FIELDS[field];
    if (!mapped || errors[mapped]) continue;

    if (code === "NotBlank" || code === "NotNull") {
      errors[mapped] = REQUIRED[mapped];
    } else if (code === "Size") {
      errors[mapped] = TOO_LONG;
    } else {
      errors[mapped] = INVALID[mapped] ?? "Neispravan unos";
    }
  }

  return errors;
}

export function toOrderRequest(
  values: CheckoutValues,
  lines: CartLine[],
): OrderRequest {
  return {
    customer: {
      name: values.name.trim(),
      email: values.email.trim(),
      phone: values.phone.trim(),
      shippingAddress: values.address.trim(),
    },
    items: lines.map(({ variantId, quantity }) => ({ variantId, quantity })),
  };
}

const STOCK_HINT = "Smanjite količinu ili uklonite stavku u korpi.";

export function insufficientStockMessage(variantIds: number[], lines: VariantLine[]): string {
  const names = variantIds.flatMap((id) => {
    const line = lines.find((candidate) => candidate.variantId === id);
    if (!line) return [];
    const hasVariant = Object.keys(line.attributes).length > 0;
    return [hasVariant ? `${line.productName} (${describeAttributes(line.attributes)})` : line.productName];
  });

  return names.length > 0
    ? `Nema dovoljno na stanju: ${names.join(", ")}. ${STOCK_HINT}`
    : `Nekih stavki nema dovoljno na stanju. ${STOCK_HINT}`;
}
