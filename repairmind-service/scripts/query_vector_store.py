from pathlib import Path

import chromadb
import torch
from sentence_transformers import SentenceTransformer

PROJECT_ROOT = Path(__file__).resolve().parents[1]
chroma_path = PROJECT_ROOT / "data" / "chroma"
device = "cuda" if torch.cuda.is_available() else "cpu"

model = SentenceTransformer(
    "BAAI/bge-m3",
    device=device,
    local_files_only=True
)

print("Embedding 设备:", device)

client = chromadb.PersistentClient(
    path=str(chroma_path)
)

collection = client.get_collection(
    name="dell_inspiron_16"
)

query = "笔记本电脑无法开机，应该检查什么？"

query_vector = model.encode(
    query,
    normalize_embeddings=True
)

result = collection.query(
    query_embeddings=[query_vector.tolist()],
    n_results=5,
    include=[
        "documents",
        "metadatas",
        "distances"
    ]
)

for document, metadata, distance in zip(
        result["documents"][0],
        result["metadatas"][0],
        result["distances"][0]
):
    print("距离:", round(distance, 4))
    print("来源:", metadata)
    print(document[:300])
    print("=" * 60)
