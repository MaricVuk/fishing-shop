"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { TextField } from "@/components/text-field";
import { Button } from "@/components/ui/button";
import { logIn } from "@/lib/admin-browser";

export function LoginForm({ next }: { next: string }) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    if (email.trim() === "" || password === "") {
      setError("Unesite email i lozinku.");
      return;
    }

    setSubmitting(true);
    setError(null);
    const result = await logIn(email.trim(), password);

    if (result.kind === "ok") {
      router.replace(next);
      router.refresh();
      return;
    }

    setSubmitting(false);
    setPassword("");
    setError(result.message);
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mt-8 flex flex-col gap-5">
      {error && (
        <div role="alert" className="rounded-xl border border-destructive/60 px-4 py-3 text-sm">
          {error}
        </div>
      )}
      <TextField
        id="email"
        label="Email"
        type="email"
        value={email}
        onChange={setEmail}
        autoComplete="username"
        maxLength={100}
      />
      <TextField
        id="password"
        label="Lozinka"
        type="password"
        value={password}
        onChange={setPassword}
        autoComplete="current-password"
        maxLength={200}
      />
      <Button type="submit" size="lg" className="h-11" disabled={submitting} focusableWhenDisabled>
        {submitting ? "Prijavljujem…" : "Prijavi se"}
      </Button>
    </form>
  );
}
