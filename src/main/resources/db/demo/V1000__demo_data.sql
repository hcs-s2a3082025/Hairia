-- デモ用データ（demoプロファイルのみ）。全アカウントのパスワードは password123
INSERT INTO salons (salon_name, address) VALUES
    ('Hairia 渋谷店', '東京都渋谷区'), ('Salon Bloom 表参道', '東京都港区'), ('Atelier Mio 新宿', '東京都新宿区');
INSERT INTO users (email, password_hash, user_type, account_status)
SELECT v.email, '$2b$10$8Ghn82mJ1RRCWEQHxOm1AuUhGVfkvja67v.65ZoXGFOO8MDEeVjqG', v.t, 'ACTIVE'
FROM (VALUES ('stylist@hairia.test','HAIRDRESSER'),('stylist2@hairia.test','HAIRDRESSER'),('stylist3@hairia.test','HAIRDRESSER'),
             ('customer@hairia.test','CUSTOMER'),('customer2@hairia.test','CUSTOMER'),('customer3@hairia.test','CUSTOMER')) v(email, t);
INSERT INTO hairdressers (user_id, salon_id, display_name, gender)
SELECT u.user_id, s.salon_id, v.n, '女性'
FROM (VALUES ('stylist@hairia.test','Hairia 渋谷店','田中 美容師'),('stylist2@hairia.test','Salon Bloom 表参道','佐々木 美容師'),
             ('stylist3@hairia.test','Atelier Mio 新宿','中村 美容師')) v(email, shop, n)
JOIN users u ON u.email = v.email JOIN salons s ON s.salon_name = v.shop;
INSERT INTO customers (user_id, display_name, gender, hair_note, consent_share)
SELECT u.user_id, v.n, '女性', v.h, v.c
FROM (VALUES ('customer@hairia.test','高橋 かおり','細め・軽いくせ',TRUE),('customer2@hairia.test','林 浩子','太め・直毛',TRUE),
             ('customer3@hairia.test','田中 夏樹','普通・強いくせ',FALSE)) v(email, n, h, c)
JOIN users u ON u.email = v.email;
INSERT INTO customer_links (hairdresser_id, customer_id, link_status, consented_at)
SELECT h.hairdresser_id, c.customer_id, 'ACTIVE', now()
FROM (VALUES ('stylist@hairia.test','customer@hairia.test'),('stylist@hairia.test','customer2@hairia.test'),('stylist@hairia.test','customer3@hairia.test'),
             ('stylist2@hairia.test','customer@hairia.test'),('stylist3@hairia.test','customer@hairia.test')) v(s, c)
JOIN users su ON su.email = v.s JOIN hairdressers h ON h.user_id = su.user_id
JOIN users cu ON cu.email = v.c JOIN customers c ON c.user_id = cu.user_id;
INSERT INTO chat_rooms (link_id, room_status) SELECT link_id, 'ACTIVE' FROM customer_links;
INSERT INTO chat_messages (chat_room_id, sender_user_id, message_type, message_text)
SELECT cr.chat_room_id, su.user_id, 'TEXT', '本日はありがとうございました。ホームケアで気になる点があればご連絡ください。'
FROM customer_links l JOIN chat_rooms cr ON cr.link_id = l.link_id
JOIN hairdressers h ON h.hairdresser_id = l.hairdresser_id JOIN users su ON su.user_id = h.user_id
JOIN customers c ON c.customer_id = l.customer_id JOIN users cu ON cu.user_id = c.user_id
WHERE su.email = 'stylist@hairia.test' AND cu.email = 'customer@hairia.test';
INSERT INTO charts (customer_id, hairdresser_id, chart_title, chart_status, internal_memo)
SELECT l.customer_id, l.hairdresser_id, v.title, v.st, v.memo
FROM (VALUES ('縮毛矯正','SHARED','毛先の乾燥が強い'),('カラー','PLANNED','')) v(title, st, memo)
CROSS JOIN (SELECT l.* FROM customer_links l JOIN hairdressers h ON h.hairdresser_id = l.hairdresser_id JOIN users su ON su.user_id = h.user_id
            JOIN customers c ON c.customer_id = l.customer_id JOIN users cu ON cu.user_id = c.user_id
            WHERE su.email = 'stylist@hairia.test' AND cu.email = 'customer@hairia.test') l;
INSERT INTO treatment_histories (chart_id, treatment_type, treatment_date, treatment_content, actual_result, product_note, created_by)
SELECT ch.chart_id, ch.chart_title,
       CASE WHEN ch.chart_title = '縮毛矯正' THEN DATE '2026-07-12' ELSE DATE '2026-09-25' END,
       CASE WHEN ch.chart_title = '縮毛矯正' THEN '縮毛矯正（全体）' ELSE 'アッシュブラウン 6レベル' END,
       CASE WHEN ch.chart_title = '縮毛矯正' THEN '縮毛矯正（根元重点・毛先は軽め）' ELSE '' END,
       CASE WHEN ch.chart_title = '縮毛矯正' THEN 'ストレート剤 1剤60g / 2剤60g　放置15分' ELSE '' END,
       ch.hairdresser_id
FROM charts ch;
