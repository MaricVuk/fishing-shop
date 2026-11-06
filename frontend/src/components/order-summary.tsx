import { describeAttributes, formatMinor } from "@/lib/format";
import type { CartView } from "@/lib/cart";

export function OrderSummary({ view }: { view: CartView }) {
  return (
    <aside className="h-fit rounded-xl bg-muted p-6 lg:sticky lg:top-6">
      <h2 className="text-lg font-semibold">Vaša porudžbina</h2>
      <ul className="mt-4 flex flex-col gap-3 text-sm">
        {view.items.map(({ line, variant, lineTotalMinor }) => (
          <li key={line.variantId} className="flex justify-between gap-4">
            <div className="min-w-0">
              <p className="font-medium">{variant?.productName ?? "Nedostupan proizvod"}</p>
              {variant && (
                <p className="text-muted-foreground">
                  {describeAttributes(variant.attributes)} × {line.quantity}
                </p>
              )}
            </div>
            <p className="shrink-0 tabular-nums">{formatMinor(lineTotalMinor)}</p>
          </li>
        ))}
      </ul>
      <div className="mt-4 flex justify-between border-t pt-4 text-base font-semibold">
        <span>Ukupno</span>
        <span className="tabular-nums">{formatMinor(view.totalMinor)}</span>
      </div>
    </aside>
  );
}
