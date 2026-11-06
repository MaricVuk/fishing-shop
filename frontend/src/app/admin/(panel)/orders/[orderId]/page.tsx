import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Forbidden } from "@/components/admin/forbidden";
import { OrderStatusActions } from "@/components/admin/order-status-actions";
import { OrderStatusBadge } from "@/components/admin/order-status-badge";
import { accessTo } from "@/lib/admin-access";
import { adminGet } from "@/lib/admin-api";
import { describeChange, describeInvoice } from "@/lib/admin-orders";
import { formatDateTime } from "@/lib/admin-stock";
import type { AdminOrderDetail } from "@/lib/admin-types";
import { formatPrice } from "@/lib/format";

export const metadata: Metadata = { title: "Porudžbina" };

export default async function OrderPage({ params }: PageProps<"/admin/orders/[orderId]">) {
  const { allowed } = await accessTo("/admin/orders");
  if (!allowed) return <Forbidden />;

  const { orderId } = await params;
  if (!/^[1-9]\d{0,17}$/.test(orderId)) notFound();
  const order = await adminGet<AdminOrderDetail>(`/api/admin/orders/${orderId}`);
  if (!order) notFound();
  const invoice = describeInvoice(order.id, order.invoice);

  return (
    <>
      <Link href="/admin/orders" className="text-sm text-primary underline-offset-4 hover:underline">
        ← Porudžbine
      </Link>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-bold">Porudžbina #{order.id}</h1>
        <OrderStatusBadge status={order.status} />
      </div>
      <p className="mt-1 text-sm text-muted-foreground">Primljena {formatDateTime(order.createdAt)}</p>

      <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_20rem]">
        <section aria-labelledby="stavke">
          <h2 id="stavke" className="text-xl font-semibold">Stavke</h2>
          <div className="mt-3 overflow-x-auto rounded-xl border">
            <table className="w-full text-left text-sm">
              <thead className="bg-muted text-muted-foreground">
                <tr>
                  <th scope="col" className="px-4 py-3 font-medium">Proizvod</th>
                  <th scope="col" className="px-4 py-3 text-right font-medium">Cena</th>
                  <th scope="col" className="px-4 py-3 text-right font-medium">Količina</th>
                  <th scope="col" className="px-4 py-3 text-right font-medium">Iznos</th>
                </tr>
              </thead>
              <tbody>
                {order.items.map((item) => (
                  <tr key={item.variantId} className="border-t">
                    <td className="px-4 py-3">
                      <span className="font-medium">{item.productName}</span>
                      <span className="block font-mono text-xs text-muted-foreground">{item.sku}</span>
                    </td>
                    <td className="px-4 py-3 text-right whitespace-nowrap tabular-nums">{formatPrice(item.unitPrice)}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{item.quantity}</td>
                    <td className="px-4 py-3 text-right whitespace-nowrap tabular-nums">{formatPrice(item.lineTotal)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t">
                  <th scope="row" colSpan={3} className="px-4 py-3 text-right font-semibold">Ukupno</th>
                  <td className="px-4 py-3 text-right font-semibold whitespace-nowrap tabular-nums">{formatPrice(order.total)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </section>

        <div className="flex flex-col gap-6">
          <section aria-labelledby="kupac" className="rounded-xl border p-5">
            <h2 id="kupac" className="text-lg font-semibold">Kupac</h2>
            <dl className="mt-3 flex flex-col gap-2 text-sm">
              <div>
                <dt className="text-muted-foreground">Ime</dt>
                <dd>{order.customer.name}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Email</dt>
                <dd className="break-all">{order.customer.email}</dd>
              </div>
              {order.customer.phone && (
                <div>
                  <dt className="text-muted-foreground">Telefon</dt>
                  <dd>{order.customer.phone}</dd>
                </div>
              )}
              <div>
                <dt className="text-muted-foreground">Adresa</dt>
                <dd className="whitespace-pre-line">{order.customer.shippingAddress}</dd>
              </div>
            </dl>
          </section>
          {invoice && (
            <section aria-labelledby="racun" className="rounded-xl border p-5">
              <h2 id="racun" className="text-lg font-semibold">Račun</h2>
              <p className="mt-3 font-medium tabular-nums">{invoice.title}</p>
              <p className="mt-1 text-sm text-muted-foreground">{invoice.text}</p>
              {invoice.href && (
                <a
                  href={invoice.href}
                  download
                  className="mt-3 inline-block text-sm font-medium text-primary underline-offset-4 hover:underline"
                >
                  Preuzmi PDF
                </a>
              )}
            </section>
          )}
          <OrderStatusActions orderId={order.id} allowed={order.allowedStatuses} />
        </div>
      </div>

      <h2 className="mt-10 text-xl font-semibold">Istorija</h2>
      {order.history.length === 0 ? (
        <p className="mt-3 text-muted-foreground">Status još nije menjan.</p>
      ) : (
        <ol className="mt-3 flex flex-col gap-3">
          {order.history.map((entry) => (
            <li key={entry.id} className="rounded-xl border px-4 py-3 text-sm">
              <p>{describeChange(entry)}</p>
              <p className="mt-1 text-muted-foreground">
                {formatDateTime(entry.createdAt)} · {entry.actorEmail}
              </p>
            </li>
          ))}
        </ol>
      )}
    </>
  );
}
