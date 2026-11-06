import Link from "next/link";
import { Notice } from "@/components/notice";
import { buttonVariants } from "@/components/ui/button";

export function Forbidden() {
  return (
    <Notice
      title="Nemate pristup ovom delu"
      description="Vaša uloga ne uključuje ovu stranicu. Ako mislite da je to greška, javite se vlasniku radnje."
    >
      <Link href="/admin" className={buttonVariants({ size: "lg" })}>
        Nazad na pregled
      </Link>
    </Notice>
  );
}
