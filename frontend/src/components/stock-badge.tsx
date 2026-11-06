import { Badge } from "@/components/ui/badge";

export function StockBadge({ inStock }: { inStock: boolean }) {
  return inStock ? (
    <Badge variant="secondary" className="bg-accent text-accent-foreground">
      Na stanju
    </Badge>
  ) : (
    <Badge variant="outline" className="text-muted-foreground">
      Nema na stanju
    </Badge>
  );
}
