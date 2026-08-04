"""给分块数据补 dept_id 元数据（手册 Day33：向量数据携带 tenant/dept 元数据）。

一次性的数据标注脚本：
1. 更新 data/processed/*.json 中每个 chunk 的 dept_id 字段；
2. 更新 Chroma 集合的元数据（无需重新嵌入，update 即可）。

默认所有语料属于模具中心 dept-mold-01；检索层按 dept 强制过滤，
跨部门查询应返回空，这就是多租户数据隔离的第一道防线。
"""
from pathlib import Path

import chromadb

PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROCESSED_JSON = PROJECT_ROOT / "data" / "processed" / "dell-inspiron-16-chunks.json"
CHROMA_DIR = PROJECT_ROOT / "data" / "chroma"
COLLECTION_NAME = "dell_inspiron_16"
DEPT_ID = "dept-mold-01"

import json  # noqa: E402

with PROCESSED_JSON.open("r", encoding="utf-8") as f:
    chunks = json.load(f)

for c in chunks:
    c["dept_id"] = DEPT_ID

with PROCESSED_JSON.open("w", encoding="utf-8") as f:
    json.dump(chunks, f, ensure_ascii=False, indent=2)

client = chromadb.PersistentClient(path=str(CHROMA_DIR))
col = client.get_collection(COLLECTION_NAME)
col.update(
    ids=[c["chunk_id"] for c in chunks],
    metadatas=[{
        "device_model": c.get("device_model", "Dell Inspiron 16"),
        "page_number": c.get("page_number"),
        "source": c.get("source", "Dell Inspiron 16 Service Manual"),
        "dept_id": DEPT_ID,
    } for c in chunks],
)

print(f"已标注 {len(chunks)} 个 chunk 的 dept_id={DEPT_ID}")
