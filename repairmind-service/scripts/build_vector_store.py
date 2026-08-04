import json
from pathlib import Path

import chromadb
import torch
from sentence_transformers import SentenceTransformer

PROJECT_ROOT = Path(__file__).resolve().parents[1]

json_path = (
        PROJECT_ROOT
        / "data"
        / "processed"
        / "dell-inspiron-16-chunks.json"
)

chroma_path = PROJECT_ROOT / "data" / "chroma"
device = "cuda" if torch.cuda.is_available() else "cpu"

with json_path.open("r", encoding="utf-8") as file:
    chunks = json.load(file)

model = SentenceTransformer(
    "BAAI/bge-m3",
    device=device,
    local_files_only=True
)

print("Embedding 设备:", device)

documents = [
    chunk["text"]
    for chunk in chunks
]

embeddings = model.encode(
    documents,
    normalize_embeddings=True,
    batch_size=4,
    show_progress_bar=True
)

client = chromadb.PersistentClient(
    path=str(chroma_path)
)

collection = client.get_or_create_collection(
    name="dell_inspiron_16",
    metadata={"hnsw:space": "cosine"}
)

collection.upsert(
    ids=[
        chunk["chunk_id"]
        for chunk in chunks
    ],
    documents=documents,
    embeddings=embeddings.tolist(),
    metadatas=[
        {
            "device_model": "Dell Inspiron 16",
            "page_number": chunk["page_number"],
            "source": "Dell Inspiron 16 Service Manual"
        }
        for chunk in chunks
    ]

)

print("写入数量:", collection.count())
