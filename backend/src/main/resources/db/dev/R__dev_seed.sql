CREATE TEMP TABLE seed_catalog (
    category_slug TEXT,
    product_name  TEXT,
    brand         TEXT,
    description   TEXT,
    sku           TEXT,
    attributes    JSONB,
    price         NUMERIC(12, 2),
    stock         INTEGER
) ON COMMIT DROP;

INSERT INTO seed_catalog VALUES
    ('stapovi', 'Shimano Catana', 'Shimano', 'Svestran štap za spin ribolov, lagan i osetljiv.', 'CAT-240', '{"length":"2.4m"}', 4500.00, 5),
    ('stapovi', 'Shimano Catana', 'Shimano', 'Svestran štap za spin ribolov, lagan i osetljiv.', 'CAT-270', '{"length":"2.7m"}', 4900.00, 2),
    ('stapovi', 'Daiwa Ninja X Spin', 'Daiwa', 'Pouzdan spin štap za sve nivoe iskustva.', 'NIX-240', '{"length":"2.4m"}', 5200.00, 8),
    ('stapovi', 'Daiwa Ninja X Spin', 'Daiwa', 'Pouzdan spin štap za sve nivoe iskustva.', 'NIX-270', '{"length":"2.7m"}', 5600.00, 0),
    ('stapovi', 'Savage Gear SG2 Spin', 'Savage Gear', 'Brz štap za varalice srednje težine.', 'SG2-210', '{"length":"2.1m"}', 11900.00, 3),
    ('stapovi', 'Savage Gear SG2 Spin', 'Savage Gear', 'Brz štap za varalice srednje težine.', 'SG2-240', '{"length":"2.4m"}', 12900.00, 4),
    ('stapovi', 'Fox Rage Warrior', 'Fox Rage', 'Robustan štap za grabljivice.', 'FRW-270', '{"length":"2.7m","action":"brza"}', 8900.00, 6),
    ('stapovi', 'Mikado Sensei Feeder', 'Mikado', 'Fider štap za ribolov na dnu.', 'MSF-330', '{"length":"3.3m"}', 7400.00, 4),
    ('stapovi', 'Mikado Sensei Feeder', 'Mikado', 'Fider štap za ribolov na dnu.', 'MSF-360', '{"length":"3.6m"}', 7900.00, 1),
    ('masinice', 'Shimano Sienna FG', 'Shimano', 'Mašinica sa prednjom kočnicom za svakodnevni ribolov.', 'SIE-2500', '{"size":"2500"}', 4200.00, 10),
    ('masinice', 'Shimano Sienna FG', 'Shimano', 'Mašinica sa prednjom kočnicom za svakodnevni ribolov.', 'SIE-4000', '{"size":"4000"}', 4800.00, 7),
    ('masinice', 'Daiwa Ninja LT', 'Daiwa', 'Laka mašinica glatkog hoda.', 'NLT-3000', '{"size":"3000"}', 7900.00, 4),
    ('masinice', 'Okuma Ceymar', 'Okuma', 'Pristupačna mašinica za početnike.', 'CEY-30', '{"size":"30"}', 5400.00, 0),
    ('masinice', 'Okuma Ceymar', 'Okuma', 'Pristupačna mašinica za početnike.', 'CEY-40', '{"size":"40"}', 5900.00, 0),
    ('masinice', 'Mikado Nihonto', 'Mikado', 'Mašinica za feeder i ribolov na dnu.', 'NIH-6000', '{"size":"6000"}', 6800.00, 3),
    ('najlon-i-upletenica', 'Berkley Trilene XL', 'Berkley', 'Mekan i otporan monofilni najlon.', 'TXL-025', '{"diameter":"0.25mm"}', 1100.00, 40),
    ('najlon-i-upletenica', 'Berkley Trilene XL', 'Berkley', 'Mekan i otporan monofilni najlon.', 'TXL-030', '{"diameter":"0.30mm"}', 1200.00, 25),
    ('najlon-i-upletenica', 'Power Pro Spectra', 'Power Pro', 'Upletenica visoke nosivosti, bez rastezanja.', 'PPS-013', '{"diameter":"0.13mm"}', 3900.00, 12),
    ('najlon-i-upletenica', 'Power Pro Spectra', 'Power Pro', 'Upletenica visoke nosivosti, bez rastezanja.', 'PPS-017', '{"diameter":"0.17mm"}', 4100.00, 0),
    ('najlon-i-upletenica', 'Shimano Kairiki 8', 'Shimano', 'Osmostruka upletenica, glatka i tiha.', 'KAI-015', '{"diameter":"0.15mm"}', 5600.00, 9),
    ('najlon-i-upletenica', 'Mikado Fluorocarbon Leader', 'Mikado', 'Fluorokarbonski predvez, skoro nevidljiv pod vodom.', 'MFL-040', '{"diameter":"0.40mm"}', 1800.00, 20),
    ('varalice', 'Rapala Original Floater', 'Rapala', 'Klasičan plivajući vobler.', 'ROF-S', '{"color":"srebrna"}', 1500.00, 15),
    ('varalice', 'Rapala Original Floater', 'Rapala', 'Klasičan plivajući vobler.', 'ROF-P', '{"color":"grgeč"}', 1500.00, 11),
    ('varalice', 'Savage Gear Line Thru Pike', 'Savage Gear', 'Realističan vobler za štuku.', 'SGP-SH', '{"color":"ukljeva"}', 3200.00, 5),
    ('varalice', 'Savage Gear Line Thru Pike', 'Savage Gear', 'Realističan vobler za štuku.', 'SGP-FT', '{"color":"vatreni tigar"}', 3200.00, 0),
    ('varalice', 'Mepps Aglia', 'Mepps', 'Obrtna blešnja za pastrmku i grgeča.', 'MAG-3S', '{"size":"3","color":"srebrna"}', 750.00, 30),
    ('varalice', 'Mepps Aglia', 'Mepps', 'Obrtna blešnja za pastrmku i grgeča.', 'MAG-3G', '{"size":"3","color":"zlatna"}', 750.00, 18),
    ('varalice', 'Westin Shad Teez', 'Westin', 'Gumeni šad sa pokretnim repom.', 'WST-09', '{"length":"9cm"}', 1300.00, 14),
    ('udice-i-pribor', 'Mustad Ultra Point', 'Mustad', 'Oštre udice za svaku vrstu mamca.', 'MUP-04', '{"size":"4"}', 380.00, 100),
    ('udice-i-pribor', 'Mustad Ultra Point', 'Mustad', 'Oštre udice za svaku vrstu mamca.', 'MUP-06', '{"size":"6"}', 360.00, 100),
    ('udice-i-pribor', 'Fox Matrix Landing Net', 'Fox', 'Podmetač za bezbedno vađenje ribe.', 'FMN-50', '{"size":"50cm"}', 4800.00, 3),
    ('udice-i-pribor', 'Daiwa Tackle Box', 'Daiwa', 'Kutija sa pregradama za varalice i pribor.', 'DTB-L', '{"size":"velika"}', 3100.00, 6),
    ('udice-i-pribor', 'Daiwa Tackle Box', 'Daiwa', 'Kutija sa pregradama za varalice i pribor.', 'DTB-M', '{"size":"srednja"}', 2500.00, 8);

INSERT INTO products (category_id, name, brand, description)
SELECT DISTINCT ON (s.product_name) c.id, s.product_name, s.brand, s.description
FROM seed_catalog s
JOIN categories c ON c.slug = s.category_slug
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.name = s.product_name)
ORDER BY s.product_name;

INSERT INTO product_variants (product_id, sku, attributes, price)
SELECT p.id, s.sku, s.attributes, s.price
FROM seed_catalog s
JOIN products p ON p.name = s.product_name
ON CONFLICT (sku) DO UPDATE SET attributes = EXCLUDED.attributes;

INSERT INTO inventory (variant_id, quantity)
SELECT v.id, s.stock
FROM seed_catalog s
JOIN product_variants v ON v.sku = s.sku
ON CONFLICT (variant_id) DO NOTHING;
