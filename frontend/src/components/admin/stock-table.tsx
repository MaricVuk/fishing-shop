import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { describeAttributes } from "@/lib/format";
import type { StockRow } from "@/lib/admin-types";

export function StockTable({ rows }: { rows: StockRow[] }) {
  return (
    <div className="mt-6 overflow-x-auto rounded-xl border">
      <table className="w-full text-left text-sm">
        <thead className="bg-muted text-muted-foreground">
          <tr>
            <th scope="col" className="px-4 py-3 font-medium">Proizvod</th>
            <th scope="col" className="px-4 py-3 font-medium">SKU</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">Slobodno</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">Rezervisano</th>
            <th scope="col" className="px-4 py-3"><span className="sr-only">Akcije</span></th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.variantId} className="border-t">
              <td className="px-4 py-3">
                <span className="font-medium">{row.productName}</span>
                <span className="block text-muted-foreground">{describeAttributes(row.attributes)}</span>
                {!row.active && (
                  <Badge variant="outline" className="mt-1">
                    Nije u prodaji
                  </Badge>
                )}
              </td>
              <td className="px-4 py-3 font-mono text-xs">{row.sku}</td>
              <td className="px-4 py-3 text-right tabular-nums">
                {row.quantity === 0 ? (
                  <Badge variant="destructive">0</Badge>
                ) : (
                  <span className="font-semibold">{row.quantity}</span>
                )}
              </td>
              <td className="px-4 py-3 text-right tabular-nums text-muted-foreground">{row.reserved}</td>
              <td className="px-4 py-3 text-right">
                <Link
                  href={`/admin/stock/${row.variantId}`}
                  className="text-primary underline-offset-4 hover:underline"
                  aria-label={`Detalji: ${row.productName}, ${row.sku}`}
                >
                  Detalji
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
