import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DEFAULT_SORT, type ProductQuery } from "@/lib/types";

export function SearchForm({ query }: { query: ProductQuery }) {
  return (
    <form
      action="/products"
      method="get"
      role="search"
      className="flex w-full gap-2 sm:max-w-md"
    >
      {query.category && (
        <input type="hidden" name="category" value={query.category} />
      )}
      {query.sort !== DEFAULT_SORT && (
        <input type="hidden" name="sort" value={query.sort} />
      )}
      <label htmlFor="q" className="sr-only">
        Pretraži proizvode
      </label>
      <Input
        id="q"
        name="q"
        type="search"
        defaultValue={query.q}
        placeholder="Naziv ili brend"
        maxLength={100}
        className="h-10"
      />
      <Button type="submit" className="h-10 px-4">
        <Search aria-hidden />
        Pretraži
      </Button>
    </form>
  );
}
