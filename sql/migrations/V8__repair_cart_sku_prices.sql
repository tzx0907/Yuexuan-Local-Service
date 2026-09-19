-- Backfill carts created by the legacy mini-program, which sent only a
-- display specification (for example "48瓶") and therefore retained the
-- base product price instead of the SKU price.
UPDATE shopping_cart sc
JOIN product_sku ps
  ON ps.dish_id = sc.dish_id
 AND ps.status = 1
 AND ps.spec_value = CASE
     WHEN INSTR(sc.dish_flavor, ':') > 0 THEN SUBSTRING_INDEX(sc.dish_flavor, ':', -1)
     ELSE sc.dish_flavor
 END
SET sc.sku_id = ps.id,
    sc.amount = ps.price,
    sc.dish_flavor = CONCAT(ps.spec_name, ':', ps.spec_value)
WHERE sc.dish_id IS NOT NULL
  AND sc.sku_id IS NULL
  AND sc.dish_flavor IS NOT NULL
  AND sc.dish_flavor <> '';
