import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Forbidden } from "@/components/admin/forbidden";
import { MovementHistory } from "@/components/admin/movement-history";
import { CountForm, ReceiptForm } from "@/components/admin/stock-forms";
import { Badge } from "@/components/ui/badge";
import { accessTo } from "@/lib/admin-access";
import { adminGet } from "@/lib/admin-api";
import type { StockMovement, StockRow } from "@/lib/admin-types";
import { describeAttributes } from "@/lib/format";

export const metadata: Metadata = { title: "Varijanta u magacinu" };

export default async function StockVariantPage({ params }: PageProps<"/admin/stock/[variantId]">) {
  const { allowed } = await accessTo("/admin/stock");
  if (!allowed) return <Forbidden />;

  const { variantId: raw } = await params;
  if (!/^[1-9]\d{0,17}$/.test(raw)) notFound();

  const [row, movements] = await Promise.all([
    adminGet<StockRow>(`/api/admin/stock/${raw}`),
    adminGet<StockMovement[]>(`/api/admin/stock/${raw}/movements`),
  ]);
  if (!row) notFound();

  return (
    <>
      <Link href="/admin/stock" className="text-sm text-primary underline-offset-4 hover:underline">
        ← Magacin
      </Link>
      <div className="mt-3 flex flex-wrap items-start justify-between gap-6">
        <div>
          <h1 className="text-3xl font-bold">{row.productName}</h1>
          <p className="mt-1 text-muted-foreground">
            {describeAttributes(row.attributes)} · <span className="font-mono text-xs">{row.sku}</span>
          </p>
          {!row.active && (
            <Badge variant="outline" className="mt-2">
              Nije u prodaji
            </Badge>
          )}
        </div>
        <div className="rounded-xl border px-6 py-4 text-right">
          <p className="text-sm text-muted-foreground">Na stanju</p>
          <p className="text-4xl font-bold tabular-nums">{row.quantity}</p>
        </div>
      </div>

      <div className="mt-8 grid gap-6 md:grid-cols-2">
        <ReceiptForm variantId={row.variantId} />
        <CountForm variantId={row.variantId} currentQuantity={row.quantity} />
      </div>

      <h2 className="mt-10 text-xl font-semibold">Istorija kretanja</h2>
      <MovementHistory movements={movements ?? []} />
    </>
  );
}
