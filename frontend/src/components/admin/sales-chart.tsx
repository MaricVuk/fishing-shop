"use client";

import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts";
import { type ChartConfig, ChartContainer, ChartTooltip, ChartTooltipContent } from "@/components/ui/chart";
import { bucketLabel, bucketTitle, chartValue, formatAmount, formatCount } from "@/lib/admin-reports";
import type { SalesReport } from "@/lib/admin-types";
import { cn } from "@/lib/utils";

const config = {
  revenue: { label: "Prihod", color: "var(--primary)" },
} satisfies ChartConfig;

const compact = new Intl.NumberFormat("sr-Latn-RS", { notation: "compact", maximumFractionDigits: 1 });

export function SalesChart({ report, className }: { report: SalesReport; className?: string }) {
  const data = report.buckets.map((bucket) => ({
    start: bucket.start,
    revenue: chartValue(bucket.revenue),
    revenueText: formatAmount(bucket.revenue),
    orders: bucket.orders,
  }));

  return (
    <ChartContainer config={config} className={cn("aspect-auto h-72 w-full", className)} aria-hidden="true">
      <BarChart data={data} margin={{ left: 4, right: 4 }}>
        <CartesianGrid vertical={false} />
        <XAxis
          dataKey="start"
          tickLine={false}
          axisLine={false}
          minTickGap={16}
          tickFormatter={(start: string) => bucketLabel(start, report.granularity)}
        />
        <YAxis
          tickLine={false}
          axisLine={false}
          width={56}
          tickFormatter={(value: number) => compact.format(value)}
        />
        <ChartTooltip
          cursor={{ fill: "var(--muted)" }}
          content={
            <ChartTooltipContent
              labelFormatter={(_, payload) => {
                const start = payload?.[0]?.payload?.start;
                return typeof start === "string" ? bucketTitle(start, report.granularity) : "";
              }}
              formatter={(_, __, item) => (
                <span className="flex w-full justify-between gap-4">
                  <span className="text-muted-foreground">{formatCount(item.payload.orders)} porudžbina</span>
                  <span className="font-medium tabular-nums">{item.payload.revenueText}</span>
                </span>
              )}
            />
          }
        />
        <Bar dataKey="revenue" fill="var(--color-revenue)" radius={[3, 3, 0, 0]} />
      </BarChart>
    </ChartContainer>
  );
}
