"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { logOut } from "@/lib/admin-browser";

export function LogoutButton() {
  const router = useRouter();
  const [state, setState] = useState<"idle" | "busy" | "failed">("idle");

  async function handleClick() {
    setState("busy");
    if (await logOut()) {
      router.replace("/admin/login");
      router.refresh();
      return;
    }
    setState("failed");
  }

  return (
    <div className="flex items-center gap-3">
      {state === "failed" && (
        <span role="alert" className="text-sm text-destructive">
          Odjava nije uspela.
        </span>
      )}
      <Button variant="outline" onClick={handleClick} disabled={state === "busy"} focusableWhenDisabled>
        {state === "busy" ? "Odjavljujem…" : "Odjavi se"}
      </Button>
    </div>
  );
}
