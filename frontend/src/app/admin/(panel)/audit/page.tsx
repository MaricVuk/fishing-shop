import type { Metadata } from "next";
import Link from "next/link";
import { Forbidden } from "@/components/admin/forbidden";
import { buttonVariants } from "@/components/ui/button";
import { accessTo } from "@/lib/admin-access";
import { adminGet } from "@/lib/admin-api";
import { formatDateTime } from "@/lib/admin-stock";
import {
  AUDIT_TYPES,
  auditEntityLabel,
  auditHref,
  auditTypeFilterLabel,
  describeAudit,
  parseAuditQuery,
} from "@/lib/admin-team";
import type { AdminPage, AuditEntry } from "@/lib/admin-types";
import { cn } from "@/lib/utils";

export const metadata: Metadata = { title: "Audit log" };

const PAGE_SIZE = 50;

function entityHref(entry: AuditEntry): string | null {
  if (entry.entityType === "ORDER") return `/admin/orders/${entry.entityId}`;
  if (entry.entityType === "VARIANT") return `/admin/stock/${entry.entityId}`;
  if (entry.entityType === "EMPLOYEE") return `/admin/employees/${entry.entityId}`;
  return null;
}

export default async function AuditPage({ searchParams }: PageProps<"/admin/audit">) {
  const { allowed } = await accessTo("/admin/audit");
  if (!allowed) return <Forbidden />;

  const query = parseAuditQuery(await searchParams);
  const params = new URLSearchParams({ page: String(query.page - 1), size: String(PAGE_SIZE) });
  if (query.type) params.set("entityType", query.type);
  const result = await adminGet<AdminPage<AuditEntry>>(`/api/admin/audit?${params}`);
  const entries = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;
  const filters = [null, ...AUDIT_TYPES] as const;

  return (
    <>
      <h1 className="text-3xl font-bold">Audit log</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Svaka izmena zalihe, porudžbine i naloga, najnovije prvo. Zapisi se ne mogu menjati ni brisati.
      </p>

      <nav aria-label="Filter po vrsti" className="mt-6 -mx-1 flex gap-1 overflow-x-auto">
        {filters.map((type) => (
          <Link
            key={type ?? "all"}
            href={auditHref(query, { type })}
            aria-current={query.type === type ? "page" : undefined}
            className={cn(
              "rounded-lg px-3 py-2 text-sm whitespace-nowrap hover:bg-muted",
              query.type === type ? "bg-muted font-semibold" : "text-muted-foreground",
            )}
          >
            {type ? auditTypeFilterLabel(type) : "Sve"}
          </Link>
        ))}
      </nav>

      {entries.length === 0 ? (
        <p className="mt-6 rounded-xl bg-muted px-6 py-10 text-muted-foreground">Još nema zapisa.</p>
      ) : (
        <div className="mt-4 overflow-x-auto rounded-xl border">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted text-muted-foreground">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">Vreme</th>
                <th scope="col" className="px-4 py-3 font-medium">Ko</th>
                <th scope="col" className="px-4 py-3 font-medium">Šta</th>
                <th scope="col" className="px-4 py-3 font-medium">Izmena</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => {
                const href = entityHref(entry);
                const label = auditEntityLabel(entry.entityType, entry.entityId);
                return (
                  <tr key={entry.id} className="border-t align-top">
                    <td className="px-4 py-3 whitespace-nowrap">{formatDateTime(entry.createdAt)}</td>
                    <td className="px-4 py-3 break-all">{entry.actorEmail}</td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {href ? (
                        <Link href={href} className="text-primary underline-offset-4 hover:underline">
                          {label}
                        </Link>
                      ) : (
                        label
                      )}
                    </td>
                    <td className="px-4 py-3">{describeAudit(entry)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <nav aria-label="Strane" className="mt-6 flex items-center gap-3">
          {query.page > 1 && (
            <Link href={auditHref(query, { page: query.page - 1 })} className={buttonVariants({ variant: "outline" })}>
              Novije
            </Link>
          )}
          <span className="text-sm text-muted-foreground">
            Strana {query.page} od {totalPages}
          </span>
          {query.page < totalPages && (
            <Link href={auditHref(query, { page: query.page + 1 })} className={buttonVariants({ variant: "outline" })}>
              Starije
            </Link>
          )}
        </nav>
      )}
    </>
  );
}
