-- 商品图片更新 SQL（基于 MinIO presigned URL）
-- 注意：presigned URL 7天过期，建议后续将 bucket 设为 public 并使用永久 URL

UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/0fae83da2b1fc73cd6242a335c9fe7dc.jpg' WHERE id = 500008;  -- 酸辣凉拌土豆丝
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/1779a0546ab68dc279e60ff2b762b35d.jpg' WHERE id = 500022;  -- 葱花米饭
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/5deb6589b6f30bb498a7c04b77ae866b.jpg' WHERE id = 500023;  -- 番茄鱼汤
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/f4e1de2fbf995883f038a8fd55bf4d4a.jpg' WHERE id = 500024;  -- 红油拌面
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/00469f592239ab3f8f581a3bfbc85cfa.jpg' WHERE id = 500025;  -- 番茄菠菜鸡蛋汤
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/ebe0f975d43118cd626a0a63d3545176.jpg' WHERE id = 500026;  -- 砂锅老鸭汤
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/10870b2460d3b921ae5a176693d24e37.jpg' WHERE id = 500033;  -- 酸豆角拌肉丝
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/3be3f0a9c9968138d82fd8c14d0b3cd8.jpg' WHERE id = 500034;  -- 清炖排骨汤
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/cd03e1ef92229e120181ec55d4e2b2c0.jpg' WHERE id = 500035;  -- 番茄炒蛋
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/12be76aac31e08d9ec28c2338d84f93a.jpg' WHERE id = 500036;  -- 蒜蓉肉片蒸茄子
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/fcd6d2e7969bc1047d625e2dfb164130.jpg' WHERE id = 500038;  -- 蒜蓉清炒青菜
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/f8da9acfa3d70861bb6810bdfe1db2ae.jpg' WHERE id = 500041;  -- 酸豆角炒肉丝
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/0236d7a6841914cb501f1740d13adece.jpg' WHERE id = 500042;  -- 排骨粉丝清汤
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/34de565f1317ef51a29aab47e0da6db3.jpg' WHERE id = 500043;  -- 西红柿炒鸡蛋
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/1143e25b15a037184d17ecd269498911.jpg' WHERE id = 500044;  -- 肉末蒸茄子
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/deee0cf5e87aea4fed44f426438972e8.jpg' WHERE id = 500045;  -- 老北京炸酱面
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/29c4c954a8489e6b08bcd213902398cc.jpg' WHERE id = 500046;  -- 清炒绿叶时蔬
UPDATE t_product SET cover = 'http://127.0.0.1:9000/product-picture/2026-09-01/4f1a5bd069c273747f42c14dd0592a46.jpg' WHERE id = 500047;  -- 皮蛋瘦肉粥
