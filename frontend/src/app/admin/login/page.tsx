import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { LoginForm } from "@/components/admin/login-form";
import { safeNextPath } from "@/lib/admin-auth";
import { getCurrentEmployee } from "@/lib/admin-session";
import { SHOP_NAME } from "@/lib/config";

export const metadata: Metadata = { title: "Prijava" };

export default async function LoginPage({ searchParams }: PageProps<"/admin/login">) {
  const raw = (await searchParams).next;
  const next = safeNextPath(Array.isArray(raw) ? raw[0] : raw);

  if (await getCurrentEmployee()) redirect(next);

  return (
    <main id="sadrzaj" tabIndex={-1} className="flex flex-1 items-center justify-center px-4 py-16 outline-none">
      <div className="w-full max-w-sm">
        <p className="font-display text-xl font-bold tracking-tight text-primary">{SHOP_NAME}</p>
        <h1 className="mt-2 text-3xl font-bold">Prijava zaposlenih</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Prijavite se nalogom koji vam je dao vlasnik radnje.
        </p>
        <LoginForm next={next} />
      </div>
    </main>
  );
}
