import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

export function TextField({
  id,
  label,
  value,
  onChange,
  error,
  type = "text",
  autoComplete,
  maxLength,
  optional = false,
  multiline = false,
  inputMode,
  hint,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  type?: string;
  autoComplete?: string;
  maxLength?: number;
  optional?: boolean;
  multiline?: boolean;
  inputMode?: "numeric" | "text" | "email";
  hint?: string;
}) {
  const shared = {
    id,
    name: id,
    value,
    maxLength,
    autoComplete,
    "aria-invalid": error ? true : undefined,
    "aria-describedby": [error ? `${id}-error` : null, hint ? `${id}-hint` : null].filter(Boolean).join(" ") || undefined,
  };

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
        {optional && (
          <span className="font-normal text-muted-foreground"> (opciono)</span>
        )}
      </label>
      {multiline ? (
        <Textarea
          {...shared}
          rows={3}
          onChange={(event) => onChange(event.target.value)}
        />
      ) : (
        <Input
          {...shared}
          type={type}
          inputMode={inputMode}
          className="h-10"
          onChange={(event) => onChange(event.target.value)}
        />
      )}
      {hint && (
        <p id={`${id}-hint`} className="text-sm text-muted-foreground">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
