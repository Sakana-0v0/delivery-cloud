# -*- coding: utf-8 -*-
"""
菜品数据爬虫 — CLI 入口

使用 Pipeline 架构，可插拔处理器链式调用
"""

import sys
import json
import logging
import argparse
from pathlib import Path
from datetime import datetime

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger('crawler')


def load_config() -> dict:
    """加载配置文件"""
    import yaml
    config_path = Path(__file__).parent / "config.yaml"
    if not config_path.exists():
        logger.error(f"配置文件不存在: {config_path}")
        logger.error("请复制 config.yaml.example 为 config.yaml 并填写配置")
        sys.exit(1)
    
    with open(config_path, 'r', encoding='utf-8') as f:
        config = yaml.safe_load(f)
    
    # 检查必需配置
    required = ['database', 'minio']
    for section in required:
        if section not in config:
            logger.error(f"配置缺少必需项: {section}")
            sys.exit(1)
    
    return config


def main():
    parser = argparse.ArgumentParser(
        description='菜品数据爬虫（Pipeline 架构）',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例：
  python crawler.py --limit 50
  python crawler.py --limit 50 --import
  python crawler.py --limit 10 --output sql > import.sql
  python crawler.py --limit 5 --dry-run
  python crawler.py --limit 50 --skip-images
        """
    )
    parser.add_argument('--limit', type=int, default=50,
                       help='爬取数量 (默认: 50)')
    parser.add_argument('--import', dest='do_import', action='store_true',
                       help='直接导入数据库')
    parser.add_argument('--output', choices=['json', 'sql'], default='json',
                       help='输出格式 (默认: json)')
    parser.add_argument('--dry-run', action='store_true',
                       help='只打印，不写入')
    parser.add_argument('--skip-images', action='store_true',
                       help='跳过图片上传')
    parser.add_argument('--yes', action='store_true',
                       help='跳过导入确认')
    
    args = parser.parse_args()
    
    # 加载配置
    logger.info("加载配置文件...")
    config = load_config()
    config['limit'] = args.limit
    config['skip_images'] = args.skip_images
    
    # 导入 Pipeline 组件
    from pipeline.orchestrator import build_pipeline
    from db import DatabaseClient
    
    # 1. 构建并运行 Pipeline
    logger.info("开始构建 Pipeline...")
    pipeline = build_pipeline(config)
    items = pipeline.run([])
    
    if not items:
        logger.error("Pipeline 未产出任何数据")
        sys.exit(1)
    
    logger.info(f"Pipeline 执行完成，共 {len(items)} 条数据")
    
    # 2. Dry-run
    if args.dry_run:
        logger.info("=== DRY RUN 模式 ===")
        for i in items[:5]:
            print(f"  - {i.name} ({i.category_name}) - ¥{i.real_price}")
        if len(items) > 5:
            print(f"  ... 还有 {len(items) - 5} 条")
        return
    
    # 3. 输出
    db = DatabaseClient(config)
    
    if args.output == 'sql' or args.do_import:
        if args.do_import:
            # 直接导入
            if not args.yes:
                confirm = input(f"将写入 {len(items)} 条到 t_product。继续？(y/n): ")
                if confirm.lower() != 'y':
                    logger.info("已取消")
                    return
            
            logger.info("开始导入数据库...")
            success, failed = db.insert_products(items)
            logger.info(f"导入完成: 成功 {success} 条, 失败 {failed} 条")
        else:
            # 输出 SQL
            sql = db.generate_sql(items)
            print(sql)
    else:
        # JSON 输出
        output_file = Path(__file__).parent / "output" / f"products_{datetime.now():%Y%m%d_%H%M%S}.json"
        output_file.parent.mkdir(exist_ok=True)
        
        # 转 dict
        data = []
        for item in items:
            d = {
                'fid': item.fid,
                'category_id': item.category_id,
                'name': item.name,
                'cover': item.minio_cover or item.cover_url or '',
                'description': item.description,
                'norm_price': item.norm_price,
                'real_price': item.real_price,
                'stock': item.stock,
                'sales': item.sales,
                'status': item.status,
                'area': item.area,
            }
            data.append(d)
        
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        
        logger.info(f"JSON 已保存: {output_file}")
    
    logger.info("=== 完成 ===")


if __name__ == '__main__':
    main()
