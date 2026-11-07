import type { Metadata } from "next";
import { connection } from "next/server";
import { Barlow_Condensed, Hanken_Grotesk } from "next/font/google";
import "./globals.css";
import { SHOP_DESCRIPTION, SHOP_NAME } from "@/lib/config";

const body = Hanken_Grotesk({
  variable: "--font-hanken",
  subsets: ["latin", "latin-ext"],
  display: "swap",
});

const display = Barlow_Condensed({
  variable: "--font-barlow",
  subsets: ["latin", "latin-ext"],
  weight: ["600", "700"],
  display: "swap",
});

export const metadata: Metadata = {
  title: { default: SHOP_NAME, template: `%s | ${SHOP_NAME}` },
  description: SHOP_DESCRIPTION,
};

export default async function RootLayout({ children }: LayoutProps<"/">) {
  await connection();
  return (
    <html lang="sr-Latn" className={`${body.variable} ${display.variable} h-full`}>
      <body className="flex min-h-full flex-col">{children}</body>
    </html>
  );
}
