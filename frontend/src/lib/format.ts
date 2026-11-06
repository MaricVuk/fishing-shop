const currency = new Intl.NumberFormat("sr-Latn-RS", {
  style: "currency",
  currency: "RSD",
});

export function formatPrice(amount: number): string {
  return currency.format(amount);
}

export function productCountLabel(count: number): string {
  const singular = count % 10 === 1 && count % 100 !== 11;
  return `${count} ${singular ? "proizvod" : "proizvoda"}`;
}
