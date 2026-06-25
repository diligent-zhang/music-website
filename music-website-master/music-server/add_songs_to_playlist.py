#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""将新歌曲添加到歌单中"""

import pymysql
import random

MYSQL_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "root",
    "database": "tp_music",
    "charset": "utf8mb4",
}

# 新建歌单（使用已有歌单图片）
NEW_SONG_LISTS = [
    ("华语金曲精选", "/img/songListPic/q0ZyCw22PCiTG2LX_A2kew==_109951163594989759.jpg", "精选华语流行金曲", "华语"),
    ("欧美流行精选", "/img/songListPic/vLSB9-NGsd4CLYf_4ShGww==_109951163609572271.jpg", "欧美最热流行歌曲", "欧美"),
    ("K-Pop韩流热歌", "/img/songListPic/92NWlGo76ha-if-WMK3vCg==_1410673428769729.jpg", "韩国流行音乐精选", "日韩"),
    ("经典老歌回顾", "/user01/songlist/123.jpg", "那些年一起听过的经典", "华语"),
    ("日系动漫歌曲", "/img/songListPic/QHD2Uy2y9ktndbK1UKgdgg==_18611433325258133.jpg", "经典动漫歌曲合集", "日韩"),
    ("影视原声大碟", "/user01/songlist/109951163271025942.jpg", "电影电视剧原声配乐", "BGM"),
    ("纯音乐之旅", "/user01/songlist/109951163609743240.jpg", "放松心情的纯音乐", "轻音乐"),
    ("周杰伦全集", "/img/songListPic/zhunizouguobansheng.jpg", "周杰伦所有经典歌曲", "华语"),
    ("Beyond光辉岁月", "/user01/songlist/a32415ca9a21f6f9a1d99b2731f224b5d319c424.jpg", "Beyond乐队经典作品", "华语"),
    ("Billboard年度金曲", "/user01/songlist/19080924789030458.jpg", "Billboard年度热门歌曲", "欧美"),
    ("抖音热歌榜", "/img/songListPic/q0ZyCw22PCiTG2LX_A2kew==_109951163594989759.jpg", "抖音最火歌曲", "华语"),
    ("粤语经典珍藏", "/img/songListPic/vLSB9-NGsd4CLYf_4ShGww==_109951163609572271.jpg", "粤语金曲合集", "粤语"),
]

def get_connection():
    return pymysql.connect(**MYSQL_CONFIG)


def main():
    print("=" * 60)
    print("歌曲添加到歌单")
    print("=" * 60)

    conn = get_connection()
    try:
        with conn.cursor() as cursor:
            # 获取新增歌曲 ID（id > 116 的是新增的）
            cursor.execute("SELECT id, name, singer_id FROM song WHERE id > 116")
            new_songs = cursor.fetchall()
            print(f"\n新增歌曲: {len(new_songs)} 首")

            # 获取已有歌单图片
            cursor.execute("SELECT DISTINCT pic FROM song_list WHERE pic IS NOT NULL AND pic != ''")
            existing_pics = [row[0] for row in cursor.fetchall()]
            print(f"可用歌单封面: {len(existing_pics)} 张")

            # 创建新歌单
            new_list_ids = []
            for title, pic, intro, style in NEW_SONG_LISTS:
                cursor.execute("SELECT id FROM song_list WHERE title = %s", (title,))
                row = cursor.fetchone()
                if row:
                    new_list_ids.append(row[0])
                    print(f"  歌单已存在: {title} (id={row[0]})")
                else:
                    cursor.execute(
                        """INSERT INTO song_list (title, pic, introduction, style)
                        VALUES (%s, %s, %s, %s)""",
                        (title, pic, intro, style)
                    )
                    new_id = cursor.lastrowid
                    new_list_ids.append(new_id)
                    print(f"  新建歌单: {title} (id={new_id})")
            conn.commit()

            # 也使用部分已有歌单
            cursor.execute("SELECT id FROM song_list ORDER BY id LIMIT 10")
            existing_ids = [row[0] for row in cursor.fetchall()]
            all_list_ids = list(set(new_list_ids + existing_ids))
            print(f"\n参与分配的歌单: {len(all_list_ids)} 个")

            # 打乱歌曲顺序，均匀分配到歌单
            random.seed(42)
            new_songs = random.sample(new_songs, len(new_songs))
            all_list_ids = random.sample(all_list_ids, len(all_list_ids))

            # 每个歌单分配约 200 首
            songs_per_list = max(150, len(new_songs) // len(all_list_ids))
            print(f"每个歌单约分配: {songs_per_list} 首")

            # 分批插入 list_song
            batch = []
            total = 0
            for i, (song_id, song_name, singer_id) in enumerate(new_songs):
                # 每首歌分配到 1-3 个歌单
                num_lists = random.choices([1, 2, 3], weights=[0.5, 0.35, 0.15])[0]
                assigned = random.sample(all_list_ids, min(num_lists, len(all_list_ids)))
                for lid in assigned:
                    batch.append((song_id, lid))

                if len(batch) >= 200:
                    with conn.cursor() as c:
                        c.executemany(
                            "INSERT IGNORE INTO list_song (song_id, song_list_id) VALUES (%s, %s)",
                            batch
                        )
                    conn.commit()
                    total += len(batch)
                    print(f"  进度: {total} 条关联记录")
                    batch = []

            # 插入剩余
            if batch:
                with conn.cursor() as c:
                    c.executemany(
                        "INSERT IGNORE INTO list_song (song_id, song_list_id) VALUES (%s, %s)",
                        batch
                    )
                conn.commit()
                total += len(batch)

            print(f"\n共创建 {total} 条歌单-歌曲关联")

            # 验证
            with conn.cursor() as c:
                c.execute("SELECT COUNT(*) FROM list_song")
                total_ls = c.fetchone()[0]
                c.execute("SELECT COUNT(DISTINCT song_id) FROM list_song")
                distinct_songs = c.fetchone()[0]
                c.execute("SELECT COUNT(DISTINCT song_list_id) FROM list_song")
                distinct_lists = c.fetchone()[0]
            print(f"\n验证结果:")
            print(f"  list_song 总记录: {total_ls}")
            print(f"  涉及歌曲: {distinct_songs}")
            print(f"  涉及歌单: {distinct_lists}")

    finally:
        conn.close()

    print("\n完成！")


if __name__ == "__main__":
    main()
