"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { X } from "lucide-react";
import {
  ADMIN_EVENT_NAMES,
  type AdminNotice,
  REFRESH_DELAY_MS,
  addNotice,
  describeAdminEvent,
  reconnectDelay,
  refreshesOn,
  streamUrl,
} from "@/lib/admin-events";

const NOTICE_LIFETIME_MS = 20_000;

export function LiveEvents() {
  const [notices, setNotices] = useState<AdminNotice[]>([]);
  const router = useRouter();
  const pathname = usePathname();
  const pathnameRef = useRef(pathname);

  useEffect(() => {
    pathnameRef.current = pathname;
  }, [pathname]);

  useEffect(() => {
    let source: EventSource | null = null;
    let stopped = false;
    let attempt = 0;
    let lastEventId: string | null = null;
    let refresh: number | undefined;
    let retry: number | undefined;
    const lifetimes = new Set<number>();

    const show = (event: MessageEvent<string>) => {
      if (event.lastEventId) lastEventId = event.lastEventId;
      const notice = describeAdminEvent(event.type, event.data);
      if (!notice) return;
      setNotices((current) => addNotice(current, notice));
      if (refreshesOn(pathnameRef.current) && refresh === undefined) {
        refresh = window.setTimeout(() => {
          refresh = undefined;
          router.refresh();
        }, REFRESH_DELAY_MS);
      }
      const lifetime = window.setTimeout(() => {
        lifetimes.delete(lifetime);
        setNotices((current) => current.filter((existing) => existing.key !== notice.key));
      }, NOTICE_LIFETIME_MS);
      lifetimes.add(lifetime);
    };

    // The browser retries a dropped stream by itself, but an error response (the server busy, the proxy
    // answering while the backend restarts) closes the EventSource for good, so the page opens a new one.
    const handleError = async () => {
      if (stopped) return;
      let status: number | null = null;
      try {
        status = (await fetch("/api/auth/me", { cache: "no-store" })).status;
      } catch {
        status = null;
      }
      if (stopped) return;
      if (status === 401) {
        stopped = true;
        source?.close();
        return;
      }
      if (source?.readyState === EventSource.CLOSED && retry === undefined) {
        retry = window.setTimeout(() => {
          retry = undefined;
          if (!stopped) connect();
        }, reconnectDelay(attempt++));
      }
    };

    const connect = () => {
      source = new EventSource(streamUrl(lastEventId));
      source.addEventListener("open", () => {
        attempt = 0;
      });
      ADMIN_EVENT_NAMES.forEach((name) => source?.addEventListener(name, show));
      source.addEventListener("error", handleError);
    };

    connect();

    return () => {
      stopped = true;
      window.clearTimeout(refresh);
      window.clearTimeout(retry);
      lifetimes.forEach((lifetime) => window.clearTimeout(lifetime));
      source?.close();
    };
  }, [router]);

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
