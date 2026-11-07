"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { TextField } from "@/components/text-field";
import { Button } from "@/components/ui/button";
import { adminPost } from "@/lib/admin-browser";
import { focusFirstInvalid } from "@/lib/focus";
import { validatePassword } from "@/lib/admin-team";

type Errors = { current?: string; next?: string; repeat?: string };

export function ChangePasswordForm() {
  const router = useRouter();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [repeat, setRepeat] = useState("");
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: "success" | "error"; text: string } | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const found: Errors = {};
    if (current === "") found.current = "Unesite trenutnu lozinku.";
    const problem = validatePassword(next);
    if (problem) found.next = problem;
    else if (next === current) found.next = "Nova lozinka mora da se razlikuje od trenutne.";
    if (repeat !== next) found.repeat = "Lozinke se ne poklapaju.";
    setErrors(found);
    setMessage(null);
    if (Object.keys(found).length > 0) {
      focusFirstInvalid(found, [["current", "current-password"], ["next", "new-password"], ["repeat", "repeat-password"]]);
      return;
    }

    setBusy(true);
    const result = await adminPost("/api/auth/password", { currentPassword: current, newPassword: next });
    setBusy(false);

    if (result.kind === "ok") {
      router.replace("/admin/login?changed=1");
      router.refresh();
    } else if (result.kind === "invalid" && result.fields.includes("currentPassword")) {
      setErrors({ current: "Trenutna lozinka nije tačna." });
    } else if (result.kind === "unauthorized") {
      router.replace("/admin/login?next=/admin/account");
    } else {
      setMessage({ tone: "error", text: "Lozinka nije promenjena. Pokušajte ponovo kasnije." });
    }
  }

  return (
    <form method="post" onSubmit={submit} noValidate className="flex flex-col gap-4 rounded-xl border p-5">
      <h2 className="text-lg font-semibold">Promena lozinke</h2>
      <TextField id="current-password" label="Trenutna lozinka" type="password" value={current} onChange={(v) => { setCurrent(v); setErrors((e) => ({ ...e, current: undefined })); }} error={errors.current} autoComplete="current-password" maxLength={200} />
      <TextField id="new-password" label="Nova lozinka" type="password" value={next} onChange={(v) => { setNext(v); setErrors((e) => ({ ...e, next: undefined })); }} error={errors.next} autoComplete="new-password" maxLength={64} hint="Najmanje 12 znakova." />
      <TextField id="repeat-password" label="Ponovite novu lozinku" type="password" value={repeat} onChange={(v) => { setRepeat(v); setErrors((e) => ({ ...e, repeat: undefined })); }} error={errors.repeat} autoComplete="new-password" maxLength={64} />
      <Button type="submit" size="lg" className="h-10 self-start" disabled={busy} focusableWhenDisabled>
        {busy ? "Čuvam…" : "Promeni lozinku"}
      </Button>
      <p role="status" aria-live="polite" className={message?.tone === "error" ? "text-sm text-destructive" : "text-sm text-muted-foreground"}>
        {message?.text}
      </p>
    </form>
  );
}
