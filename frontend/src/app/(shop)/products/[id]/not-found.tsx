import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";

export default function ProductNotFound() {
  return (
    <div className="flex flex-col items-start gap-3 rounded-xl bg-muted px-6 py-10">
      <h1 className="text-3xl font-bold">Proizvod nije pronađen</h1>
      <p className="max-w-prose text-muted-foreground">
        Proizvod ne postoji ili više nije u ponudi.
      </p>
      <Link href="/products" className={buttonVariants({ size: "lg" })}>
        Nazad na proizvode
      </Link>
    </div>
  );
}
