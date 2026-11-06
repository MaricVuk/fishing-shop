const currency = new Intl.NumberFormat("sr-Latn-RS", {
  style: "currency",
  currency: "RSD",
});

export function formatPrice(amount: number): string {
  return currency.format(amount);
}

const ATTRIBUTE_LABELS: Record<string, string> = {
  length: "Dužina",
  size: "Veličina",
  color: "Boja",
  diameter: "Prečnik",
  action: "Akcija",
};

export function attributeLabel(key: string): string {
  return ATTRIBUTE_LABELS[key] ?? key.charAt(0).toUpperCase() + key.slice(1);
}

export function describeAttributes(attributes: Record<string, string>): string {
  const entries = Object.entries(attributes);

  if (entries.length === 0) return "Osnovna varijanta";

  return entries
    .map(([key, value]) => `${attributeLabel(key)}: ${value}`)
    .join(" · ");
}

export function productCountLabel(count: number): string {
  const singular = count % 10 === 1 && count % 100 !== 11;
  return `${count} ${singular ? "proizvod" : "proizvoda"}`;
}
