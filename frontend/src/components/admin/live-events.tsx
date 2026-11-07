"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { ADMIN_EVENT_NAMES, type AdminNotice, addNotice, describeAdminEvent } from "@/lib/admin-events";

const NOTICE_LIFETIME_MS = 20_000;

export function LiveEvents() {
  const [notices, setNotices] = useState<AdminNotice[]>([]);

  useEffect(() => {
    const source = new EventSource("/api/admin/events");
    let stopped = false;

    const show = (event: MessageEvent<string>) => {
      const notice = describeAdminEvent(event.type, event.data);
      if (!notice) return;
      setNotices((current) => addNotice(current, notice));
      window.setTimeout(
        () => setNotices((current) => current.filter((existing) => existing.key !== notice.key)),
        NOTICE_LIFETIME_MS,
      );
    };

    const checkSession = async () => {
      if (stopped) return;
      try {
        const me = await fetch("/api/auth/me", { cache: "no-store" });
        if (me.status === 401) {
          stopped = true;
          source.close();
        }
      } catch {
        // the browser keeps retrying the stream on its own
      }
    };

    ADMIN_EVENT_NAMES.forEach((name) => source.addEventListener(name, show));
    source.addEventListener("error", checkSession);

    return () => {
      stopped = true;
      source.close();
    };
  }, []);

  const dismiss = (key: string) => setNotices((current) => current.filter((notice) => notice.key !== key));

  return (
    <div
      aria-live="polite"
      aria-label="Obaveštenja"
      className="pointer-events-none fixed right-4 bottom-4 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2"
    >
      {notices.map((notice) => (
        <div
          key={notice.key}
          className="pointer-events-auto flex items-start gap-3 rounded-xl border bg-background px-4 py-3 text-sm shadow-lg"
        >
          <div className="min-w-0 flex-1">
            <p className="font-semibold">{notice.title}</p>
            <p className="mt-0.5 break-words text-muted-foreground">{notice.detail}</p>
            <Link href={notice.href} className="mt-1 inline-block text-primary underline-offset-4 hover:underline">
              {notice.linkLabel}
            </Link>
          </div>
          <button
            type="button"
            onClick={() => dismiss(notice.key)}
            className="-mt-1 -mr-2 rounded-md p-1.5 text-muted-foreground hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
          >
            <X className="size-4" aria-hidden="true" />
            <span className="sr-only">Zatvori obaveštenje</span>
          </button>
        </div>
      ))}
    </div>
  );
}
