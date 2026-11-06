"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { TextField } from "@/components/text-field";
import { Button } from "@/components/ui/button";
import { adminPost } from "@/lib/admin-browser";
import { statusActionLabel } from "@/lib/admin-orders";
import type { AdminOrderDetail, OrderStatus } from "@/lib/admin-types";

export function OrderStatusActions({ orderId, allowed }: { orderId: number; allowed: OrderStatus[] }) {
  const router = useRouter();
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "success" | "error"; text: string } | null>(null);

  if (allowed.length === 0) {
    return (
      <section className="rounded-xl border p-5 text-sm text-muted-foreground">
        Ova porudžbina je završena i status se više ne menja.
      </section>
    );
  }

  async function change(status: OrderStatus) {
    if (busy) return;
    const note = reason.trim();
    if (note.length > 200) {
      setMessage({ tone: "error", text: "Razlog može imati najviše 200 znakova." });
      return;
    }
    setBusy(true);
    setMessage(null);
    const result = await adminPost<AdminOrderDetail>(`/api/admin/orders/${orderId}/status`, {
      status,
      reason: note === "" ? null : note,
    });
    setBusy(false);

    if (result.kind === "ok") {
      setConfirmingCancel(false);
      setReason("");
      setMessage({ tone: "success", text: "Status je promenjen." });
      router.refresh();
    } else if (result.kind === "unauthorized") {
      router.replace("/admin/login?next=" + encodeURIComponent(window.location.pathname));
    } else if (result.kind === "conflict") {
      setMessage({ tone: "error", text: "Neko je u međuvremenu promenio status. Prikaz je osvežen." });
      router.refresh();
    } else if (result.kind === "forbidden") {
      setMessage({ tone: "error", text: "Nemate pravo da menjate porudžbine." });
    } else {
      setMessage({ tone: "error", text: "Promena nije sačuvana. Pokušajte ponovo za trenutak." });
    }
  }

  const forward = allowed.filter((status) => status !== "CANCELLED");

  return (
    <section aria-labelledby="status-akcije" className="flex flex-col gap-3 rounded-xl border p-5">
      <h2 id="status-akcije" className="text-lg font-semibold">Sledeći korak</h2>
      {forward.map((status) => (
        <Button key={status} size="lg" className="h-10" disabled={busy} focusableWhenDisabled onClick={() => change(status)}>
          {statusActionLabel(status)}
        </Button>
      ))}
      {allowed.includes("CANCELLED") &&
        (confirmingCancel ? (
          <div className="flex flex-col gap-3 rounded-lg bg-muted p-3">
            <p className="text-sm">Otkazivanje vraća sve stavke na stanje. Ovo ne može da se poništi.</p>
            <TextField id="cancel-reason" label="Razlog" value={reason} onChange={setReason} maxLength={200} optional />
            <div className="flex flex-wrap gap-2">
              <Button variant="destructive" disabled={busy} focusableWhenDisabled onClick={() => change("CANCELLED")}>
                {busy ? "Otkazujem…" : "Da, otkaži"}
              </Button>
              <Button variant="ghost" disabled={busy} onClick={() => setConfirmingCancel(false)}>
                Odustani
              </Button>
            </div>
          </div>
        ) : (
          <Button variant="outline" size="lg" className="h-10" disabled={busy} onClick={() => setConfirmingCancel(true)}>
            {statusActionLabel("CANCELLED")}
          </Button>
        ))}
      <p role="status" aria-live="polite" className={message?.tone === "error" ? "text-sm text-destructive" : "text-sm text-muted-foreground"}>
        {message?.text}
      </p>
    </section>
  );
}
