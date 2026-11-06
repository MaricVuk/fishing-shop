"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { adminPost } from "@/lib/admin-browser";
import type { AdminJob } from "@/lib/admin-types";

export function RetryJobButton({ jobId }: { jobId: number }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function retry() {
    if (busy) return;
    setBusy(true);
    setError(null);
    const result = await adminPost<AdminJob>(`/api/admin/jobs/${jobId}/retry`, {});
    setBusy(false);

    if (result.kind === "ok") {
      router.refresh();
    } else if (result.kind === "unauthorized") {
      router.replace("/admin/login?next=" + encodeURIComponent(window.location.pathname + window.location.search));
    } else if (result.kind === "conflict" || result.kind === "not-found") {
      setError("Posao je u međuvremenu već pokrenut. Prikaz je osvežen.");
      router.refresh();
    } else if (result.kind === "forbidden") {
      setError("Samo vlasnik može da ponovi posao.");
    } else {
      setError("Posao nije ponovo pokrenut. Pokušajte za trenutak.");
    }
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <Button size="sm" variant="outline" disabled={busy} focusableWhenDisabled onClick={retry}>
        {busy ? "Pokrećem…" : "Ponovi"}
      </Button>
      <p role="status" aria-live="polite" className="text-xs text-destructive">
        {error}
      </p>
    </div>
  );
}
