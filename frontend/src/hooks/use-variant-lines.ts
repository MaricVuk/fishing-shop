import { useEffect, useState } from "react";
import { fetchVariantLines } from "@/lib/browser-api";
import type { VariantLine } from "@/lib/types";

type Result = { ids: number[]; attempt: number; lines: VariantLine[] | null };

const NO_LINES: VariantLine[] = [];

export function useVariantLines(variantIds: number[]) {
  const key = [...variantIds].sort((a, b) => a - b).join(",");
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<Result | null>(null);

  useEffect(() => {
    if (key === "") return;

    const ids = key.split(",").map(Number);
    const controller = new AbortController();

    fetchVariantLines(ids, controller.signal)
      .then((lines) => setResult({ ids, attempt, lines }))
      .catch(() => {
        if (!controller.signal.aborted) setResult({ ids, attempt, lines: null });
      });

    return () => controller.abort();
  }, [key, attempt]);

  const usable =
    result !== null &&
    result.attempt === attempt &&
    variantIds.every((id) => result.ids.includes(id))
      ? result
      : null;

  const status: "loading" | "ready" | "error" =
    key === "" ? "ready" : usable === null ? "loading" : usable.lines === null ? "error" : "ready";

  return {
    status,
    lines: usable?.lines ?? NO_LINES,
    retry: () => setAttempt((value) => value + 1),
  };
}
