import { formatDateTime, movementLabel } from "@/lib/admin-stock";
import type { StockMovement } from "@/lib/admin-types";

export function MovementHistory({ movements }: { movements: StockMovement[] }) {
  if (movements.length === 0) {
    return <p className="mt-4 text-muted-foreground">Za ovu varijantu još nema kretanja.</p>;
  }

  return (
    <div className="mt-4 overflow-x-auto rounded-xl border">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Poslednja kretanja zalihe, najnovije prvo</caption>
        <thead className="bg-muted text-muted-foreground">
          <tr>
            <th scope="col" className="px-4 py-3 font-medium">Vreme</th>
            <th scope="col" className="px-4 py-3 font-medium">Vrsta</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">Promena</th>
            <th scope="col" className="px-4 py-3 text-right font-medium">Stanje posle</th>
            <th scope="col" className="px-4 py-3 font-medium">Napomena</th>
          </tr>
        </thead>
        <tbody>
          {movements.map((movement) => (
            <tr key={movement.id} className="border-t">
              <td className="px-4 py-3 whitespace-nowrap">{formatDateTime(movement.createdAt)}</td>
              <td className="px-4 py-3">{movementLabel(movement.type)}</td>
              <td className="px-4 py-3 text-right font-semibold tabular-nums">
                {movement.quantityChange > 0 ? `+${movement.quantityChange}` : movement.quantityChange}
              </td>
              <td className="px-4 py-3 text-right tabular-nums">{movement.quantityAfter}</td>
              <td className="px-4 py-3 text-muted-foreground">
                {movement.orderId !== null ? `Porudžbina #${movement.orderId}` : (movement.reason ?? "—")}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
