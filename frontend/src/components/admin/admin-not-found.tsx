import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";

export function AdminNotFound() {
  return (
    <div className="flex flex-col items-start gap-3 rounded-xl bg-muted px-6 py-10">
      <h1 className="text-3xl font-bold">Nije pronađeno</h1>
      <p className="max-w-prose text-muted-foreground">
        Ova porudžbina, varijanta ili nalog ne postoji ili je uklonjen.
      </p>
      <Link href="/admin" className={buttonVariants({ size: "lg" })}>
        Nazad na pregled
      </Link>
    </div>
  );
}
