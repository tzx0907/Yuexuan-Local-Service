-- 组合内所有已绑定 SKU 的组成价必须取实时 SKU 售价，随后按“单买总价 8 折”重算组合价。
USE `Yuexuan-Local-Service`;

START TRANSACTION;

UPDATE setmeal_dish sd
JOIN product_sku ps ON ps.id=sd.sku_id
SET sd.price=ps.price,
    sd.sku_snapshot=CONCAT(ps.spec_name,'：',ps.spec_value);

UPDATE setmeal s
SET s.price=(SELECT ROUND(SUM(sd.price*sd.copies)*0.80,2) FROM setmeal_dish sd WHERE sd.setmeal_id=s.id)
WHERE s.name IN ('居家补货组合','家庭清洁组合','宠物日常组合');

COMMIT;
