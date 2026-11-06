"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Notice } from "@/components/notice";
import { OrderSummary } from "@/components/order-summary";
import { TextField } from "@/components/text-field";
import { Button, buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useCart } from "@/hooks/use-cart";
import { useHydrated } from "@/hooks/use-hydrated";
import { useVariantLines } from "@/hooks/use-variant-lines";
import { submitOrder } from "@/lib/browser-api";
import { buildCartView } from "@/lib/cart";
import {
  mapServerErrors,
  toOrderRequest,
  validateCheckout,
  type CheckoutField,
  type CheckoutValues,
  type FieldErrors,
} from "@/lib/checkout";
import { saveLastOrder } from "@/lib/last-order";

const FIELD_ORDER: CheckoutField[] = ["name", "email", "phone", "address"];

export function CheckoutForm() {
  const router = useRouter();
  const hydrated = useHydrated();
  const cart = useCart();
  const { status, lines: variantLines, retry } = useVariantLines(
    cart.lines.map((line) => line.variantId),
  );

  const [values, setValues] = useState<CheckoutValues>({
    name: "",
    email: "",
    phone: "",
    address: "",
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function update(field: CheckoutField, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  function focusFirstInvalid(found: FieldErrors) {
    const first = FIELD_ORDER.find((field) => found[field]);
    if (first) document.getElementById(first)?.focus();
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const found = validateCheckout(values);
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) {
      focusFirstInvalid(found);
      return;
    }

    setSubmitting(true);
    const result = await submitOrder(toOrderRequest(values, cart.lines));

    if (result.kind === "created") {
      saveLastOrder(result.order);
      cart.clear();
      router.push("/checkout/success");
      return;
    }

    setSubmitting(false);

    if (result.kind === "invalid") {
      const serverErrors = mapServerErrors(result.errors);
      setErrors(serverErrors);
      setFormError("Proverite označena polja i pokušajte ponovo.");
      focusFirstInvalid(serverErrors);
    } else if (result.kind === "unavailable") {
      setFormError("Neke stavke više nisu dostupne. Pregledajte korpu pre nego što nastavite.");
    } else {
      setFormError("Porudžbina nije poslata. Proverite vezu i pokušajte ponovo.");
    }
  }

  if (!hydrated) {
    return (
      <div className="mt-8 flex flex-col gap-4" aria-busy="true" aria-label="Učitavanje">
        <Skeleton className="h-10 w-full max-w-xl" />
        <Skeleton className="h-10 w-full max-w-xl" />
        <Skeleton className="h-24 w-full max-w-xl" />
      </div>
    );
  }

  if (cart.lines.length === 0 && !submitting) {
    return (
      <Notice title="Korpa je prazna" description="Dodajte proizvode pre nego što završite porudžbinu.">
        <Link href="/products" className={buttonVariants({ size: "lg" })}>
          Pogledaj proizvode
        </Link>
      </Notice>
    );
  }

  if (status === "error") {
    return (
      <Notice
        title="Porudžbina trenutno nije dostupna"
        description="Nismo uspeli da učitamo cene i dostupnost. Pokušajte ponovo za trenutak."
      >
        <Button size="lg" onClick={retry}>
          Pokušaj ponovo
        </Button>
      </Notice>
    );
  }

  const view = buildCartView(cart.lines, variantLines);

  if (status === "ready" && view.hasProblems) {
    return (
      <Notice
        title="U korpi ima nedostupnih stavki"
        description="Uklonite ih u korpi pre nego što završite porudžbinu."
      >
        <Link href="/cart" className={buttonVariants({ size: "lg" })}>
          Nazad na korpu
        </Link>
      </Notice>
    );
  }

  return (
    <div className="mt-6 grid gap-8 lg:grid-cols-[1fr_22rem] lg:gap-12">
      <form onSubmit={handleSubmit} noValidate className="flex max-w-xl flex-col gap-5">
        {formError && (
          <div role="alert" className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
            {formError}
            {" "}
            <Link href="/cart" className="underline underline-offset-4">
              Pregledaj korpu
            </Link>
          </div>
        )}
        <TextField
          id="name"
          label="Ime i prezime"
          value={values.name}
          onChange={(value) => update("name", value)}
          error={errors.name}
          autoComplete="name"
          maxLength={150}
        />
        <TextField
          id="email"
          label="Adresa e-pošte"
          type="email"
          value={values.email}
          onChange={(value) => update("email", value)}
          error={errors.email}
          autoComplete="email"
          maxLength={200}
        />
        <TextField
          id="phone"
          label="Telefon"
          type="tel"
          value={values.phone}
          onChange={(value) => update("phone", value)}
          error={errors.phone}
          autoComplete="tel"
          maxLength={50}
          optional
        />
        <TextField
          id="address"
          label="Adresa za isporuku"
          value={values.address}
          onChange={(value) => update("address", value)}
          error={errors.address}
          autoComplete="street-address"
          maxLength={500}
          multiline
        />
        <Button
          type="submit"
          size="lg"
          className="h-11 self-start px-6"
          disabled={submitting || status === "loading"}
        >
          {submitting ? "Šaljem porudžbinu…" : "Pošalji porudžbinu"}
        </Button>
      </form>
      {status === "ready" ? (
        <OrderSummary view={view} />
      ) : (
        <Skeleton className="h-48 w-full rounded-xl" />
      )}
    </div>
  );
}
