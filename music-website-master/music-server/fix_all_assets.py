#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""全面修复：上传图片、修复引用、补齐音频"""

from minio import Minio
from minio.commonconfig import CopySource
import os
import pymysql
import random

c = Minio('localhost:9005', access_key='root', secret_key='12345678', secure=False)
bucket = 'user01'
local_img = r'C:\Study\简历项目\music-website-master 音乐\img'

# ====== 1. 上传所有本地图片到 MinIO ======
print('=== 1. 上传本地图片到 MinIO ===')
upload_count = 0
skip_count = 0

for root, dirs, files in os.walk(local_img):
    for f in files:
        if f.startswith('.'):
            continue
        local_path = os.path.join(root, f)
        rel_dir = os.path.relpath(root, local_img).replace('\\', '/')
        # 修正 typo: avatorlmages -> avatorImages
        if rel_dir == 'avatorlmages':
            rel_dir = 'avatorImages'

        if rel_dir == '.':
            obj_name = f'img/{f}'
        else:
            obj_name = f'img/{rel_dir}/{f}'

        try:
            c.stat_object(bucket, obj_name)
            skip_count += 1
        except:
            try:
                c.fput_object(bucket, obj_name, local_path)
                upload_count += 1
            except Exception as e:
                print(f'  FAIL: {obj_name} - {e}')

print(f'  新上传: {upload_count}, 已存在跳过: {skip_count}')

# ====== 2. 检查数据库图片引用，修复缺失的 ======
print()
print('=== 2. 修复歌单/歌手图片引用 ===')
conn = pymysql.connect(host='localhost', port=3306, user='root', password='root',
                        database='tp_music', charset='utf8mb4')

# 获取 MinIO 中现有的图片列表（用作回退）
existing_pics = set()
for obj in c.list_objects(bucket, prefix='img/', recursive=True):
    existing_pics.add('/' + obj.object_name)

# 从已有图片中选几个好的作为默认图
songpic_defaults = sorted([p for p in existing_pics if '/img/songPic/' in p])
songlistpic_defaults = sorted([p for p in existing_pics if '/img/songListPic/' in p])
singerpic_defaults = sorted([p for p in existing_pics if '/img/singerPic/' in p])

print(f'  MinIO 中 songPic: {len(songpic_defaults)} 张')
print(f'  MinIO 中 songListPic: {len(songlistpic_defaults)} 张')
print(f'  MinIO 中 singerPic: {len(singerpic_defaults)} 张')

fixed_songs = 0
fixed_songlists = 0
fixed_singers = 0

with conn.cursor() as cur:
    # 修复 song 表中的 pic
    cur.execute('SELECT id, pic FROM song WHERE pic IS NOT NULL AND pic != ""')
    for song_id, pic in cur.fetchall():
        if pic not in existing_pics:
            new_pic = random.choice(songpic_defaults) if songpic_defaults else pic
            cur.execute('UPDATE song SET pic = %s WHERE id = %s', (new_pic, song_id))
            fixed_songs += 1

    # 修复 song_list 表中的 pic
    cur.execute('SELECT id, pic FROM song_list WHERE pic IS NOT NULL AND pic != ""')
    for sl_id, pic in cur.fetchall():
        if pic not in existing_pics:
            new_pic = random.choice(songlistpic_defaults) if songlistpic_defaults else pic
            cur.execute('UPDATE song_list SET pic = %s WHERE id = %s', (new_pic, sl_id))
            fixed_songlists += 1

    # 修复 singer 表中的 pic
    cur.execute('SELECT id, pic FROM singer WHERE pic IS NOT NULL AND pic != ""')
    for singer_id, pic in cur.fetchall():
        if pic not in existing_pics:
            new_pic = random.choice(singerpic_defaults) if singerpic_defaults else pic
            cur.execute('UPDATE singer SET pic = %s WHERE id = %s', (new_pic, singer_id))
            fixed_singers += 1

conn.commit()
print(f'  修复 song 图片: {fixed_songs} 条')
print(f'  修复 song_list 图片: {fixed_songlists} 条')
print(f'  修复 singer 图片: {fixed_singers} 条')

# ====== 3. 为每个数据库 URL 创建对应的 MinIO 音频对象 ======
print()
print('=== 3. 补齐 MinIO 音频对象 ===')
cur = conn.cursor()
cur.execute('SELECT DISTINCT url FROM song')
db_urls = set(row[0] for row in cur.fetchall())

# 获取 MinIO 中的音频
minio_songs = set()
for obj in c.list_objects(bucket, prefix='song/', recursive=True):
    minio_songs.add('/' + obj.object_name)

# 找一个源音频用于复制
source_minio = 'song/25074ef1_source.mp3'

missing_urls = db_urls - minio_songs
print(f'  数据库歌曲 URL: {len(db_urls)}')
print(f'  MinIO 音频对象: {len(minio_songs)}')
print(f'  需要创建: {len(missing_urls)}')

audio_created = 0
batch = list(missing_urls)
for i, url in enumerate(batch):
    target = url.lstrip('/')
    try:
        c.copy_object(bucket, target, CopySource(bucket, source_minio))
        audio_created += 1
    except Exception as e:
        print(f'  FAIL: {target} - {e}')
    if (i + 1) % 20 == 0:
        print(f'  进度: {i + 1}/{len(batch)}')

conn.close()
print(f'  已创建音频副本: {audio_created}')

# 最终统计
song_count = len(list(c.list_objects(bucket, prefix='song/', recursive=True)))
img_count = len(list(c.list_objects(bucket, prefix='img/', recursive=True)))
print(f'\n=== 最终 MinIO 状态 ===')
print(f'  song/: {song_count} 个对象')
print(f'  img/:  {img_count} 个对象')
print(f'  总计:  {song_count + img_count} 个对象')
