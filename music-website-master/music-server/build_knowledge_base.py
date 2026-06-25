#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
音乐知识库构建脚本
通过 DeepSeek API 批量生成歌曲创作背景、歌词含义等知识文档
用法: python build_knowledge_base.py
"""
import pymysql
import requests
import os
import time
import json

# ============== 配置 ==============
MYSQL_CONFIG = {
    "host": "localhost", "port": 3306,
    "user": "root", "password": "root",
    "database": "tp_music", "charset": "utf8mb4",
}

DEEPSEEK_API_KEY = "your api key"
DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions"
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "rag-docs")
BATCH_SIZE = 5        # 每批 5 首歌
DELAY_SECONDS = 1.5    # 批次间延迟
TOTAL_SONGS = 300      # 处理前 300 首热门歌


def get_top_songs(limit):
    """从 MySQL 获取热门歌曲"""
    conn = pymysql.connect(**MYSQL_CONFIG)
    cursor = conn.cursor(pymysql.cursors.DictCursor)
    cursor.execute("""
        SELECT s.id, s.name, si.name as singer, s.introduction
        FROM song s
        JOIN singer si ON s.singer_id = si.id
        ORDER BY s.play_count DESC
        LIMIT %s
    """, (limit,))
    songs = cursor.fetchall()
    conn.close()
    return songs


def generate_backgrounds_batch(batch):
    """调用 DeepSeek 批量生成歌曲背景知识"""
    # 构建批量 prompt
    song_list = ""
    for i, song in enumerate(batch):
        song_list += f"{i+1}. 《{song['name']}》- 演唱者: {song['singer']}\n"

    prompt = f"""请为以下 {len(batch)} 首歌曲分别写一段知识介绍（每段 150-300 字），
内容包括：歌曲创作背景、歌词主题含义、歌曲风格特点、表达的情感。
用中文写，语气自然专业。

歌曲列表：
{song_list}

请严格按照以下格式回复（每首歌用 "---SONG---" 分隔）：
---SONG---
歌名: 歌曲名
歌手: 歌手名
内容: 歌曲介绍正文
---SONG---
歌名: 歌曲名
歌手: 歌手名
内容: 歌曲介绍正文
"""

    try:
        resp = requests.post(
            DEEPSEEK_API_URL,
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {DEEPSEEK_API_KEY}"
            },
            json={
                "model": "deepseek-chat",
                "messages": [{"role": "user", "content": prompt}],
                "temperature": 0.7,
                "max_tokens": 4000
            },
            timeout=60
        )
        if resp.status_code != 200:
            print(f"  API 调用失败: {resp.status_code} {resp.text[:200]}")
            return None
        return resp.json()["choices"][0]["message"]["content"]
    except Exception as e:
        print(f"  API 异常: {e}")
        return None


def parse_response(text, batch):
    """解析 DeepSeek 返回的批量结果"""
    results = {}
    # 按 "---SONG---" 分割
    parts = text.split("---SONG---")
    for part in parts:
        part = part.strip()
        if not part or "歌名:" not in part:
            continue
        # 提取歌名
        lines = part.strip().split("\n")
        song_name = ""
        singer_name = ""
        content_lines = []
        in_content = False
        for line in lines:
            line = line.strip()
            if line.startswith("歌名:") or line.startswith("歌名："):
                song_name = line.replace("歌名:", "").replace("歌名：", "").strip()
            elif line.startswith("歌手:") or line.startswith("歌手："):
                singer_name = line.replace("歌手:", "").replace("歌手：", "").strip()
            elif line.startswith("内容:") or line.startswith("内容："):
                in_content = True
                content_text = line.replace("内容:", "").replace("内容：", "").strip()
                if content_text:
                    content_lines.append(content_text)
            elif in_content:
                content_lines.append(line)

        # 匹配到 batch 中的歌曲
        matched_song = None
        for song in batch:
            if song_name and (song_name in song['name'] or song['name'] in song_name):
                matched_song = song
                break
        if not matched_song:
            for song in batch:
                if singer_name and singer_name in song['singer']:
                    matched_song = song
                    break
        if matched_song:
            results[matched_song['id']] = {
                "name": matched_song['name'],
                "singer": matched_song['singer'],
                "content": "\n".join(content_lines) if content_lines else part
            }
    return results


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    existing = set(f.replace(".txt", "") for f in os.listdir(OUTPUT_DIR) if f.endswith(".txt"))

    # 1. 获取歌曲列表
    print(f"从 MySQL 获取前 {TOTAL_SONGS} 首热门歌曲...")
    songs = get_top_songs(TOTAL_SONGS)
    pending = [s for s in songs if str(s['id']) not in existing]
    print(f"共 {len(songs)} 首，已存在 {len(songs) - len(pending)} 篇，待生成 {len(pending)} 篇")

    if not pending:
        print("知识库已是最新，无需更新。")
        return

    # 2. 分批处理
    total_batches = (len(pending) + BATCH_SIZE - 1) // BATCH_SIZE
    for batch_idx in range(0, len(pending), BATCH_SIZE):
        batch = pending[batch_idx:batch_idx + BATCH_SIZE]
        batch_num = batch_idx // BATCH_SIZE + 1
        print(f"\n[{batch_num}/{total_batches}] 处理 {len(batch)} 首歌: ", end="")
        print(", ".join(f"《{s['name']}》" for s in batch))

        # 调用 API
        text = generate_backgrounds_batch(batch)
        if not text:
            print("  跳过此批次（API 失败）")
            continue

        # 解析结果
        results = parse_response(text, batch)

        # 保存
        for song_id, info in results.items():
            filepath = os.path.join(OUTPUT_DIR, f"{song_id}.txt")
            with open(filepath, "w", encoding="utf-8") as f:
                f.write(f"{info['name']}|{info['singer']}\n")
                f.write(info['content'])
            print(f"  已保存: {song_id}.txt - 《{info['name']}》")

        # 为未匹配到的歌曲生成备用内容
        matched_ids = set(results.keys())
        for song in batch:
            if song['id'] not in matched_ids:
                filepath = os.path.join(OUTPUT_DIR, f"{song['id']}.txt")
                intro = song.get('introduction', '') or f"{song['singer']}演唱的《{song['name']}》，是一首广受欢迎的歌曲。"
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(f"{song['name']}|{song['singer']}\n")
                    f.write(f"《{song['name']}》由{song['singer']}演唱。" + intro)
                print(f"  已保存(备用): {song['id']}.txt - 《{song['name']}》")

        time.sleep(DELAY_SECONDS)

    print(f"\n完成！文档保存至: {OUTPUT_DIR}")
    print(f"共 {len(os.listdir(OUTPUT_DIR))} 篇")


if __name__ == "__main__":
    main()
