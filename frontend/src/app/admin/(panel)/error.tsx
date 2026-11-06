"use client";

import { Button } from "@/components/ui/button";

export default function AdminError({ retry }: { retry: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-start gap-3 rounded-xl bg-muted px-6 py-10">
      <h1 className="text-3xl font-bold">Nešto nije u redu</h1>
      <p className="max-w-prose text-muted-foreground">
        Podaci trenutno nisu dostupni. Ako se greška ponavlja, odjavite se i prijavite ponovo.
      </p>
      <Button size="lg" onClick={() => retry()}>
        Pokušaj ponovo
      </Button>
    </div>
  );
}
