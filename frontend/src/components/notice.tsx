import type { ReactNode } from "react";

export function Notice({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="mt-8 flex flex-col items-start gap-3 rounded-xl bg-muted px-6 py-10">
      <h2 className="text-xl font-semibold">{title}</h2>
      <p className="max-w-prose text-muted-foreground">{description}</p>
      {children}
    </div>
  );
}
