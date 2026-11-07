"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { TextField } from "@/components/text-field";
import { Button } from "@/components/ui/button";
import { adminPost } from "@/lib/admin-browser";
import type { AdminJob } from "@/lib/admin-types";

type Action = "retry" | "resolve";

const DONE: Record<Action, string> = {
  retry: "Posao je ponovo pokrenut.",
  resolve: "Posao je označen kao rešen.",
};

export function JobActions({ jobId, permanent }: { jobId: number; permanent: boolean }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState<{ tone: "success" | "error"; text: string } | null>(null);

  async function run(action: Action) {
    if (busy) return;
    const note = reason.trim();
    if (note.length > 200) {
      setMessage({ tone: "error", text: "Razlog može imati najviše 200 znakova." });
      return;
    }
    setBusy(true);
    setMessage(null);
    const body = action === "resolve" ? { reason: note === "" ? null : note } : {};
    const result = await adminPost<AdminJob>(`/api/admin/jobs/${jobId}/${action}`, body);
    setBusy(false);

    if (result.kind === "ok") {
      setMessage({ tone: "success", text: DONE[action] });
      router.refresh();
    } else if (result.kind === "unauthorized") {
      router.replace("/admin/login?next=" + encodeURIComponent(window.location.pathname + window.location.search));
    } else if (result.kind === "conflict") {
      setMessage({ tone: "error", text: "Neko je u međuvremenu već promenio ovaj posao. Prikaz je osvežen." });
      router.refresh();
    } else if (result.kind === "not-found") {
      setMessage({ tone: "error", text: "Posao više ne postoji." });
      router.refresh();
    } else if (result.kind === "forbidden") {
      setMessage({ tone: "error", text: "Samo vlasnik može da menja poslove." });
    } else if (result.kind === "invalid") {
      setMessage({ tone: "error", text: "Razlog sadrži znakove koji nisu dozvoljeni." });
    } else {
      setMessage({ tone: "error", text: "Izmena nije sačuvana. Pokušajte za trenutak." });
    }
  }

  return (
    <div className="flex w-52 flex-col items-start gap-2">
      <Button size="sm" variant="outline" disabled={busy} focusableWhenDisabled onClick={() => run("retry")}>
        {busy && !resolving ? "Pokrećem…" : "Ponovi"}
      </Button>
      {permanent && (
        <p className="text-xs text-muted-foreground">Ponavljanje neće pomoći dok se uzrok ne ispravi.</p>
      )}
      {resolving ? (
        <div className="flex w-full flex-col gap-2 rounded-lg bg-muted p-3">
          <TextField
            id={`resolve-reason-${jobId}`}
            label="Razlog"
            value={reason}
            onChange={setReason}
            maxLength={200}
            optional
          />
          <div className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy} focusableWhenDisabled onClick={() => run("resolve")}>
              {busy ? "Čuvam…" : "Da, rešeno"}
            </Button>
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => setResolving(false)}>
              Odustani
            </Button>
          </div>
        </div>
      ) : (
        <Button size="sm" variant="ghost" disabled={busy} onClick={() => setResolving(true)}>
          Označi kao rešeno
        </Button>
      )}
      <p
        role="status"
        aria-live="polite"
        className={message?.tone === "error" ? "text-xs text-destructive" : "text-xs text-muted-foreground"}
      >
        {message?.text}
      </p>
    </div>
  );
}
