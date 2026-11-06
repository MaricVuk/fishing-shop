export function focusFirstInvalid<K extends string>(
  errors: Partial<Record<K, string | undefined>>,
  order: readonly (readonly [K, string])[],
): void {
  const first = order.find(([field]) => errors[field]);
  if (first) document.getElementById(first[1])?.focus();
}
