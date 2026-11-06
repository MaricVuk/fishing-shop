import { Skeleton } from "@/components/ui/skeleton";

export default function Loading() {
  return (
    <div
      className="grid gap-6 lg:grid-cols-[14rem_1fr] lg:gap-10"
      aria-busy="true"
      aria-label="Učitavanje proizvoda"
    >
      <aside className="flex gap-2 lg:flex-col">
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} className="h-10 w-32 shrink-0 lg:w-full" />
        ))}
      </aside>
      <div>
        <Skeleton className="h-10 w-72 max-w-full" />
        <Skeleton className="mt-3 h-4 w-24" />
        <ul className="mt-10 grid grid-cols-2 gap-x-4 gap-y-8 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }, (_, index) => (
            <li key={index} className="flex flex-col gap-3">
              <Skeleton className="aspect-[4/3] w-full rounded-xl" />
              <Skeleton className="h-3 w-16" />
              <Skeleton className="h-5 w-full" />
              <Skeleton className="h-5 w-28" />
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
