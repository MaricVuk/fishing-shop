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

export function formatMinor(minor: number): string {
  return formatPrice(minor / 100);
}

function pluralForm(count: number, one: string, few: string, many: string): string {
  const lastDigit = count % 10;
  const lastTwoDigits = count % 100;

  if (lastDigit === 1 && lastTwoDigits !== 11) return one;
  if (lastDigit >= 2 && lastDigit <= 4 && (lastTwoDigits < 12 || lastTwoDigits > 14)) {
    return few;
  }
  return many;
}

export function itemCountLabel(count: number): string {
  return `${count} ${pluralForm(count, "stavka", "stavke", "stavki")}`;
}

export function productCountLabel(count: number): string {
  const singular = count % 10 === 1 && count % 100 !== 11;
  return `${count} ${singular ? "proizvod" : "proizvoda"}`;
}
