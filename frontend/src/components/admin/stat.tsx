import type { ReactNode } from "react";

export function Stat({ label, value, hint }: { label: string; value: string; hint?: ReactNode }) {
  return (
    <div className="min-w-0 rounded-xl border px-5 py-4">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="mt-1 text-2xl font-semibold tabular-nums [overflow-wrap:anywhere]">{value}</dd>
      {hint && <dd className="mt-1 text-sm text-muted-foreground">{hint}</dd>}
    </div>
  );
}
