import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { cache } from "react";
import { Breadcrumbs } from "@/components/breadcrumbs";
import { ProductImage } from "@/components/product-image";
import { VariantList } from "@/components/variant-list";
import { fetchProduct } from "@/lib/api";
import { parseProductQuery, productsHref } from "@/lib/query";
import type { ProductDetail } from "@/lib/types";

const ID_PATTERN = /^[1-9]\d{0,14}$/;

const loadProduct = cache(
  async (rawId: string): Promise<ProductDetail | null> => {
    if (!ID_PATTERN.test(rawId)) return null;
    return fetchProduct(Number(rawId));
  },
);

export async function generateMetadata({
  params,
}: PageProps<"/products/[id]">): Promise<Metadata> {
  const product = await loadProduct((await params).id);

  return product
    ? { title: product.name, description: product.description ?? undefined }
    : { title: "Proizvod nije pronađen" };
}

export default async function ProductPage({
  params,
}: PageProps<"/products/[id]">) {
  const product = await loadProduct((await params).id);

  if (!product) notFound();

  const categoryHref = productsHref(parseProductQuery({}), {
    category: product.category.slug,
  });

  return (
    <article>
      <Breadcrumbs
        items={[
          { label: "Proizvodi", href: "/products" },
          { label: product.category.name, href: categoryHref },
          { label: product.name },
        ]}
      />
      <div className="mt-6 grid gap-8 md:grid-cols-2 lg:gap-12">
        <ProductImage
          src={product.imageUrl}
          alt={product.name}
          sizes="(min-width: 768px) 50vw, 100vw"
          priority
        />
        <div>
          {product.brand && (
            <p className="text-sm text-muted-foreground">{product.brand}</p>
          )}
          <h1 className="mt-1 text-3xl font-bold sm:text-4xl">{product.name}</h1>
          {product.description && (
            <p className="mt-4 max-w-prose whitespace-pre-line">
              {product.description}
            </p>
          )}
          <h2 className="mt-8 mb-2 text-lg font-semibold">Varijante</h2>
          <VariantList variants={product.variants} />
        </div>
      </div>
    </article>
  );
}
