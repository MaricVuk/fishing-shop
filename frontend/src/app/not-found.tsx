import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col items-start gap-3 px-4 py-16 sm:px-6 lg:px-8">
      <h1 className="text-3xl font-bold">Stranica nije pronađena</h1>
      <p className="max-w-prose text-muted-foreground">
        Adresa koju ste uneli ne postoji ili je stranica uklonjena.
      </p>
      <Link href="/products" className={buttonVariants({ size: "lg" })}>
        Nazad na proizvode
      </Link>
    </main>
  );
}
