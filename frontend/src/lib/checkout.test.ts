import { describe, expect, it } from "vitest";
import {
  insufficientStockMessage,
  mapServerErrors,
  toOrderRequest,
  validateCheckout,
  type CheckoutValues,
} from "@/lib/checkout";
import type { VariantLine } from "@/lib/types";

const valid: CheckoutValues = {
  name: "Marko Marković",
  email: "marko@example.com",
  phone: "",
  address: "Knez Mihailova 1, Beograd",
};

describe("validateCheckout", () => {
  it("accepts valid values with an empty phone", () => {
    expect(validateCheckout(valid)).toEqual({});
  });

  it("accepts a valid phone number", () => {
    expect(validateCheckout({ ...valid, phone: "+381 60 123 4567" })).toEqual({});
  });

  it("requires name, email and address", () => {
    expect(validateCheckout({ name: " ", email: "", phone: "", address: "  " })).toEqual({
      name: "Unesite ime i prezime",
      email: "Unesite adresu e-pošte",
      address: "Unesite adresu za isporuku",
    });
  });

  it.each(["marko", "marko@", "marko@example", "mar ko@example.com", "@example.com"])(
    "rejects the email %s",
    (email) => {
      expect(validateCheckout({ ...valid, email }).email).toBe(
        "Unesite ispravnu adresu e-pošte",
      );
    },
  );

  it("ignores spaces around the email", () => {
    expect(validateCheckout({ ...valid, email: "  marko@example.com " })).toEqual({});
  });

  it.each(["abc", "12345", "060-ABC-123", "......", "1"])("rejects the phone %s", (phone) => {
    expect(validateCheckout({ ...valid, phone }).phone).toBe(
      "Unesite ispravan broj telefona",
    );
  });

  it("rejects text that is too long", () => {
    expect(validateCheckout({ ...valid, name: "x".repeat(151) }).name).toBe(
      "Uneti tekst je predugačak",
    );
    expect(validateCheckout({ ...valid, address: "x".repeat(501) }).address).toBe(
      "Uneti tekst je predugačak",
    );
  });
});

describe("mapServerErrors", () => {
  it("maps server field names and codes to form messages", () => {
    expect(
      mapServerErrors([
        { field: "customer.name", code: "NotBlank" },
        { field: "customer.email", code: "Email" },
        { field: "customer.phone", code: "Pattern" },
        { field: "customer.shippingAddress", code: "Size" },
      ]),
    ).toEqual({
      name: "Unesite ime i prezime",
      email: "Unesite ispravnu adresu e-pošte",
      phone: "Unesite ispravan broj telefona",
      address: "Uneti tekst je predugačak",
    });
  });

  it("ignores fields that are not part of the form", () => {
    expect(mapServerErrors([{ field: "items[0].quantity", code: "Min" }])).toEqual({});
  });
});

describe("toOrderRequest", () => {
  it("trims the customer data and copies only variant ids and quantities", () => {
    const request = toOrderRequest(
      { name: " Ana ", email: " ana@example.com ", phone: " ", address: " Ulica 1 " },
      [{ variantId: 7, quantity: 2 }],
    );

    expect(request).toEqual({
      customer: {
        name: "Ana",
        email: "ana@example.com",
        phone: "",
        shippingAddress: "Ulica 1",
      },
      items: [{ variantId: 7, quantity: 2 }],
    });
  });
});

function line(variantId: number, productName: string, attributes: Record<string, string>): VariantLine {
  return {
    variantId,
    productId: 1,
    productName,
    brand: null,
    sku: `SKU-${variantId}`,
    attributes,
    price: 100,
    imageUrl: null,
    inStock: true,
  };
}

describe("insufficientStockMessage", () => {
  const lines = [line(1, "Štap Feeder", { length: "2.7m" }), line(2, "Mašinica Pro", {})];

  it("names one item", () => {
    expect(insufficientStockMessage([2], lines)).toBe(
      "Nema dovoljno na stanju: Mašinica Pro. Smanjite količinu ili uklonite stavku u korpi.",
    );
  });

  it("names several items with their variant", () => {
    expect(insufficientStockMessage([1, 2], lines)).toMatch(
      /^Nema dovoljno na stanju: Štap Feeder \(.+\), Mašinica Pro\. /,
    );
  });

  it("falls back to a general message for unknown ids", () => {
    expect(insufficientStockMessage([99], lines)).toBe(
      "Nekih stavki nema dovoljno na stanju. Smanjite količinu ili uklonite stavku u korpi.",
    );
  });
});
