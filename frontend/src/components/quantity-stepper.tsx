import { Minus, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { MAX_QUANTITY } from "@/lib/cart";

export function QuantityStepper({
  value,
  onChange,
  label,
}: {
  value: number;
  onChange: (value: number) => void;
  label: string;
}) {
  return (
    <div
      role="group"
      aria-label={label}
      className="inline-flex items-center rounded-lg border"
    >
      <Button
        variant="ghost"
        size="icon"
        className="size-10"
        aria-label="Smanji količinu"
        disabled={value <= 1}
        onClick={() => onChange(value - 1)}
      >
        <Minus aria-hidden />
      </Button>
      <span className="min-w-8 text-center tabular-nums" aria-live="polite">
        {value}
      </span>
      <Button
        variant="ghost"
        size="icon"
        className="size-10"
        aria-label="Povećaj količinu"
        disabled={value >= MAX_QUANTITY}
        onClick={() => onChange(value + 1)}
      >
        <Plus aria-hidden />
      </Button>
    </div>
  );
}
