"use client";

import { Button } from "@/components/ui/button";

export default function ProductsError({ retry }: { retry: () => void }) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-xl bg-muted px-6 py-10">
      <h1 className="text-3xl font-bold">Proizvodi trenutno nisu dostupni</h1>
      <p className="max-w-prose text-muted-foreground">
        Došlo je do greške pri učitavanju kataloga. Pokušajte ponovo za
        trenutak.
      </p>
      <Button size="lg" onClick={() => retry()}>
        Pokušaj ponovo
      </Button>
    </div>
  );
}
