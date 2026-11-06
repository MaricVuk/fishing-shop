"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { TextField } from "@/components/text-field";
import { Button } from "@/components/ui/button";
import { adminPost, type AdminPostResult } from "@/lib/admin-browser";
import { focusFirstInvalid } from "@/lib/focus";
import {
  validateCount,
  validateReceipt,
  type StockFormErrors,
  type StockFormResult,
  type StockFormValues,
} from "@/lib/admin-stock";
import type { StockLevel } from "@/lib/admin-types";

type Status = { tone: "success" | "error"; text: string } | null;

const EMPTY: StockFormValues = { quantity: "", reason: "" };

function failureText(result: Exclude<AdminPostResult<StockLevel>, { kind: "ok" }>): string {
  switch (result.kind) {
    case "invalid":
      return "Server je odbio unos. Proverite količinu i napomenu.";
    case "forbidden":
      return "Nemate pravo da menjate zalihu.";
    case "not-found":
      return "Ova varijanta više ne postoji.";
    case "rejected":
      return "Server je odbio izmenu: zaliha ne sme da pređe 1.000.000, a popis ne sme da bude manji od onoga što je rezervisano za otvorene porudžbine.";
    default:
      return "Izmena nije sačuvana. Pokušajte ponovo za trenutak.";
  }
}

function useStockForm(
  validate: (values: StockFormValues) => StockFormResult,
  send: (quantity: number, reason: string | null) => Promise<AdminPostResult<StockLevel>>,
  successText: (level: StockLevel) => string,
  ids: Record<keyof StockFormValues, string>,
) {
  const router = useRouter();
  const [values, setValues] = useState<StockFormValues>(EMPTY);
  const [errors, setErrors] = useState<StockFormErrors>({});
  const [status, setStatus] = useState<Status>(null);
  const [busy, setBusy] = useState(false);

  function update(field: keyof StockFormValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    setStatus(null);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const checked = validate(values);
    if (!checked.ok) {
      setErrors(checked.errors);
      focusFirstInvalid(checked.errors, [["quantity", ids.quantity], ["reason", ids.reason]]);
      return;
    }

    setBusy(true);
    const result = await send(checked.quantity, checked.reason);
    setBusy(false);

    if (result.kind === "ok") {
      setValues(EMPTY);
      setStatus({ tone: "success", text: successText(result.data) });
      router.refresh();
    } else if (result.kind === "unauthorized") {
      router.replace("/admin/login?next=" + encodeURIComponent(window.location.pathname));
    } else {
      setStatus({ tone: "error", text: failureText(result) });
    }
  }

  return { values, errors, status, busy, update, submit };
}

function StatusLine({ status }: { status: Status }) {
  return (
    <p
      role="status"
      aria-live="polite"
      className={status?.tone === "error" ? "text-sm text-destructive" : "text-sm text-muted-foreground"}
    >
      {status?.text}
    </p>
  );
}

export function ReceiptForm({ variantId }: { variantId: number }) {
  const form = useStockForm(
    validateReceipt,
    (quantity, reason) => adminPost(`/api/admin/stock/${variantId}/receipts`, { quantity, reason }),
    (level) => `Prijem je sačuvan. Novo stanje: ${level.quantity}.`,
    { quantity: "receipt-quantity", reason: "receipt-reason" },
  );

  return (
    <form onSubmit={form.submit} noValidate className="flex flex-col gap-4 rounded-xl border p-5">
      <h2 className="text-lg font-semibold">Prijem robe</h2>
      <TextField
        id="receipt-quantity"
        label="Primljena količina"
        value={form.values.quantity}
        onChange={(value) => form.update("quantity", value)}
        error={form.errors.quantity}
        inputMode="numeric"
        maxLength={6}
      />
      <TextField
        id="receipt-reason"
        label="Napomena"
        value={form.values.reason}
        onChange={(value) => form.update("reason", value)}
        error={form.errors.reason}
        maxLength={200}
        optional
        hint="Na primer broj otpremnice."
      />
      <Button type="submit" size="lg" className="h-10 self-start" disabled={form.busy} focusableWhenDisabled>
        {form.busy ? "Čuvam…" : "Sačuvaj prijem"}
      </Button>
      <StatusLine status={form.status} />
    </form>
  );
}

export function CountForm({
  variantId,
  currentQuantity,
  reserved,
}: {
  variantId: number;
  currentQuantity: number;
  reserved: number;
}) {
  const form = useStockForm(
    validateCount,
    (countedQuantity, reason) => adminPost(`/api/admin/stock/${variantId}/counts`, { countedQuantity, reason }),
    (level) =>
      level.quantity === currentQuantity
        ? "Stanje se poklapa sa popisom, ništa nije promenjeno."
        : `Popis je sačuvan. Novo stanje za prodaju: ${level.quantity}.`,
    { quantity: "count-quantity", reason: "count-reason" },
  );

  return (
    <form onSubmit={form.submit} noValidate className="flex flex-col gap-4 rounded-xl border p-5">
      <h2 className="text-lg font-semibold">Popis</h2>
      <TextField
        id="count-quantity"
        label="Izbrojano na polici"
        value={form.values.quantity}
        onChange={(value) => form.update("quantity", value)}
        error={form.errors.quantity}
        inputMode="numeric"
        maxLength={7}
        hint={`U sistemu je ${currentQuantity} slobodno za prodaju i ${reserved} rezervisano za otvorene porudžbine, pa na polici treba da bude ${currentQuantity + reserved}.`}
      />
      <TextField
        id="count-reason"
        label="Razlog"
        value={form.values.reason}
        onChange={(value) => form.update("reason", value)}
        error={form.errors.reason}
        maxLength={200}
      />
      <Button type="submit" size="lg" className="h-10 self-start" disabled={form.busy} focusableWhenDisabled>
        {form.busy ? "Čuvam…" : "Sačuvaj popis"}
      </Button>
      <StatusLine status={form.status} />
    </form>
  );
}
