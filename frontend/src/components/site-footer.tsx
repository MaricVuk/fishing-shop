import { SHOP_NAME, SOURCE_URL } from "@/lib/config";

export function SiteFooter() {
  return (
    <footer className="border-t">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-2 px-4 py-6 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:px-8">
        <p>© 2026 {SHOP_NAME}. Demo projekat.</p>
        <a
          href={SOURCE_URL}
          className="underline underline-offset-4 hover:text-foreground"
        >
          Izvorni kod na GitHub-u
        </a>
      </div>
    </footer>
  );
}
