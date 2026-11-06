"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { TextField } from "@/components/text-field";
import { Button } from "@/components/ui/button";
import { adminPost, type AdminPostResult } from "@/lib/admin-browser";
import { focusFirstInvalid } from "@/lib/focus";
import { roleLabel } from "@/lib/admin-nav";
import { ROLES, validateNewEmployee, validatePassword, type NewEmployeeErrors } from "@/lib/admin-team";
import type { AdminEmployee, EmployeeRole } from "@/lib/admin-types";

type Message = { tone: "success" | "error"; text: string } | null;

function failure(result: Exclude<AdminPostResult<unknown>, { kind: "ok" }>): string {
  switch (result.kind) {
    case "rejected":
      return "Server je odbio unos.";
    case "conflict":
      return "Ova izmena nije dozvoljena: email je zauzet, ili bi radnja ostala bez vlasnika.";
    case "invalid":
      return "Server je odbio unos. Proverite polja.";
    case "forbidden":
      return "Samo aktivan vlasnik može da menja naloge.";
    case "not-found":
      return "Ovaj nalog više ne postoji.";
    default:
      return "Izmena nije sačuvana. Pokušajte ponovo za trenutak.";
  }
}

function MessageLine({ message }: { message: Message }) {
  return (
    <p
      role="status"
      aria-live="polite"
      className={message?.tone === "error" ? "text-sm text-destructive" : "text-sm text-muted-foreground"}
    >
      {message?.text}
    </p>
  );
}

function RoleSelect({
  id,
  value,
  onChange,
  error,
}: {
  id: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        Uloga
      </label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className="h-10 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
      >
        <option value="">Izaberite…</option>
        {ROLES.map((role) => (
          <option key={role} value={role}>
            {roleLabel(role)}
          </option>
        ))}
      </select>
      {error && (
        <p id={`${id}-error`} className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

function useAfterSave() {
  const router = useRouter();
  return (result: AdminPostResult<unknown>, success: string, setMessage: (message: Message) => void) => {
    if (result.kind === "ok") {
      setMessage({ tone: "success", text: success });
      router.refresh();
      return true;
    }
    if (result.kind === "unauthorized") {
      router.replace("/admin/login?next=" + encodeURIComponent(window.location.pathname));
      return false;
    }
    setMessage({ tone: "error", text: failure(result) });
    return false;
  };
}

export function NewEmployeeForm() {
  const afterSave = useAfterSave();
  const empty = { email: "", fullName: "", role: "", password: "" };
  const [values, setValues] = useState(empty);
  const [errors, setErrors] = useState<NewEmployeeErrors>({});
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<Message>(null);

  function update(field: keyof typeof empty, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    setMessage(null);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const checked = validateNewEmployee(values);
    if (!checked.ok) {
      setErrors(checked.errors);
      focusFirstInvalid(checked.errors, [
        ["fullName", "new-name"], ["email", "new-email"], ["role", "new-role"], ["password", "new-password"],
      ]);
      return;
    }
    setBusy(true);
    const result = await adminPost<AdminEmployee>("/api/admin/employees", checked.value);
    setBusy(false);
    if (afterSave(result, `Nalog za ${checked.value.email} je napravljen.`, setMessage)) setValues(empty);
  }

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4 self-start rounded-xl border p-5">
      <h2 className="text-lg font-semibold">Dodaj zaposlenog</h2>
      <TextField id="new-name" label="Ime i prezime" value={values.fullName} onChange={(v) => update("fullName", v)} error={errors.fullName} maxLength={150} autoComplete="off" />
      <TextField id="new-email" label="Email" type="email" value={values.email} onChange={(v) => update("email", v)} error={errors.email} maxLength={100} autoComplete="off" />
      <RoleSelect id="new-role" value={values.role} onChange={(v) => update("role", v)} error={errors.role} />
      <TextField
        id="new-password"
        label="Početna lozinka"
        type="password"
        value={values.password}
        onChange={(v) => update("password", v)}
        error={errors.password}
        maxLength={64}
        autoComplete="new-password"
        hint="Najmanje 12 znakova. Zaposleni je posle prijave menja na stranici Moj nalog."
      />
      <Button type="submit" size="lg" className="h-10 self-start" disabled={busy} focusableWhenDisabled>
        {busy ? "Čuvam…" : "Dodaj"}
      </Button>
      <MessageLine message={message} />
    </form>
  );
}

export function RoleForm({ employeeId, role }: { employeeId: number; role: EmployeeRole }) {
  const afterSave = useAfterSave();
  const [value, setValue] = useState<string>(role);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<Message>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || value === role || value === "") return;
    setBusy(true);
    const result = await adminPost(`/api/admin/employees/${employeeId}/role`, { role: value });
    setBusy(false);
    afterSave(result, "Uloga je promenjena. Zaposleni mora ponovo da se prijavi.", setMessage);
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 rounded-xl border p-5">
      <h2 className="text-lg font-semibold">Uloga</h2>
      <RoleSelect id="role" value={value} onChange={(v) => { setValue(v); setMessage(null); }} />
      <Button type="submit" size="lg" className="h-10 self-start" disabled={busy || value === role} focusableWhenDisabled>
        {busy ? "Čuvam…" : "Promeni ulogu"}
      </Button>
      <MessageLine message={message} />
    </form>
  );
}

export function EmployeeAccessForm({ employeeId, active }: { employeeId: number; active: boolean }) {
  const afterSave = useAfterSave();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<Message>(null);

  async function send() {
    setBusy(true);
    const result = await adminPost(`/api/admin/employees/${employeeId}/${active ? "deactivate" : "activate"}`, {});
    setBusy(false);
    setConfirming(false);
    afterSave(result, active ? "Nalog je isključen i zaposleni je odjavljen." : "Nalog je ponovo uključen.", setMessage);
  }

  return (
    <section className="flex flex-col gap-4 rounded-xl border p-5">
      <h2 className="text-lg font-semibold">Pristup</h2>
      <p className="text-sm text-muted-foreground">
        {active ? "Isključen nalog ne može da se prijavi, a otvorene sesije se odmah završavaju." : "Nalog je isključen."}
      </p>
      {active && confirming ? (
        <div className="flex flex-wrap gap-2">
          <Button variant="destructive" disabled={busy} focusableWhenDisabled onClick={send}>
            {busy ? "Isključujem…" : "Da, isključi"}
          </Button>
          <Button variant="ghost" disabled={busy} onClick={() => setConfirming(false)}>
            Odustani
          </Button>
        </div>
      ) : (
        <Button
          variant={active ? "outline" : "default"}
          size="lg"
          className="h-10 self-start"
          disabled={busy}
          focusableWhenDisabled
          onClick={() => (active ? setConfirming(true) : send())}
        >
          {active ? "Isključi nalog" : busy ? "Uključujem…" : "Uključi nalog"}
        </Button>
      )}
      <MessageLine message={message} />
    </section>
  );
}

export function PasswordResetForm({ employeeId }: { employeeId: number }) {
  const afterSave = useAfterSave();
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | undefined>();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<Message>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const problem = validatePassword(password);
    if (problem) {
      setError(problem);
      document.getElementById("reset-password")?.focus();
      return;
    }
    setBusy(true);
    const result = await adminPost(`/api/admin/employees/${employeeId}/password`, { password });
    setBusy(false);
    if (afterSave(result, "Nova lozinka je postavljena, a stare sesije su završene.", setMessage)) setPassword("");
  }

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4 rounded-xl border p-5">
      <h2 className="text-lg font-semibold">Nova lozinka</h2>
      <TextField
        id="reset-password"
        label="Lozinka"
        type="password"
        value={password}
        onChange={(v) => { setPassword(v); setError(undefined); setMessage(null); }}
        error={error}
        maxLength={64}
        autoComplete="new-password"
      />
      <Button type="submit" size="lg" className="h-10 self-start" disabled={busy} focusableWhenDisabled>
        {busy ? "Čuvam…" : "Postavi lozinku"}
      </Button>
      <MessageLine message={message} />
    </form>
  );
}
