import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { formatAmount, formatCount, stockLevelLabel } from "@/lib/admin-reports";
import type { BestSeller, LowStockItem } from "@/lib/admin-types";
import { describeAttributes } from "@/lib/format";

function VariantCell({ name, attributes, sku }: { name: string; attributes: Record<string, string>; sku: string }) {
  return (
    <>
      <span className="font-medium">{name}</span>
      <span className="block text-muted-foreground">
        {describeAttributes(attributes)} · {sku}
      </span>
    </>
  );
}

export function BestSellerTable({ items }: { items: BestSeller[] }) {
  return (
    <div className="mt-4 overflow-x-auto rounded-xl border">
      <table className="w-full text-left text-sm">
        <thead className="bg-muted text-muted-foreground">
          <tr>
            <th scope="col" className="px-4 py-3 text-right font-medium">#</th>
            <th scope="col" className="px-4 py-3 font-medium">Artikal</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">Prodato</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">Porudžbine</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">Prihod</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item, index) => (
            <tr key={item.variantId} className="border-t align-top">
              <td className="px-4 py-3 text-right text-muted-foreground tabular-nums">{index + 1}</td>
              <td className="px-4 py-3">
                <VariantCell name={item.productName} attributes={item.attributes} sku={item.sku} />
                {!item.active && (
                  <Badge variant="outline" className="mt-1">
                    Nije u prodaji
                  </Badge>
                )}
              </td>
              <td className="px-4 py-3 text-right whitespace-nowrap tabular-nums">{formatCount(item.quantity)} kom.</td>
              <td className="px-4 py-3 text-right tabular-nums">{formatCount(item.orders)}</td>
              <td className="px-4 py-3 text-right whitespace-nowrap tabular-nums">{formatAmount(item.revenue)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function LowStockTable({ items }: { items: LowStockItem[] }) {
  return (
    <div className="mt-4 overflow-x-auto rounded-xl border">
      <table className="w-full text-left text-sm">
        <thead className="bg-muted text-muted-foreground">
          <tr>
            <th scope="col" className="px-4 py-3 font-medium">Artikal</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">Na stanju</th>
            <th scope="col" className="px-4 py-3 font-medium"><span className="sr-only">Magacin</span></th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.variantId} className="border-t align-top">
              <td className="px-4 py-3">
                <VariantCell name={item.productName} attributes={item.attributes} sku={item.sku} />
              </td>
              <td className="px-4 py-3 text-right whitespace-nowrap">
                {item.quantity === 0 ? (
                  <Badge variant="destructive">{stockLevelLabel(0)}</Badge>
                ) : (
                  <span className="tabular-nums">{stockLevelLabel(item.quantity)}</span>
                )}
              </td>
              <td className="px-4 py-3 text-right whitespace-nowrap">
                <Link
                  href={`/admin/stock/${item.variantId}`}
                  className="text-primary underline-offset-4 hover:underline"
                >
                  Prijem robe<span className="sr-only"> za {item.productName}, {item.sku}</span>
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
