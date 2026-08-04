from pathlib import Path
import pymupdf
from langchain_text_splitters import RecursiveCharacterTextSplitter
import tiktoken
import json


PROJECT_ROOT = Path(__file__).resolve().parents[1]
pdf_path = PROJECT_ROOT / "data" / "raw" / "dell-inspiron-16-7610-带三个风扇的计算机的服务手册.pdf"
output_path = PROJECT_ROOT / "data" / "processed" / "dell-inspiron-16-chunks.json"
output_path.parent.mkdir(parents=True, exist_ok=True)
pages=[]
with pymupdf.open(pdf_path) as doc:
    print("页数：",len(doc))
    for page_number,page in enumerate(doc):
        text = page.get_text("text",sort=True)

        pages.append({
            "page_number":page_number+1,
            "text":text
        })

def split_recursive_by_token(text: str, chunk_size: int, overlap: int) -> list[str]:
    text_splitter = RecursiveCharacterTextSplitter.from_tiktoken_encoder(
      encoding_name="cl100k_base",
      chunk_size=chunk_size,
      chunk_overlap=overlap,
      separators=[
          "\n\n",
          "\n",
          "。",
          "！",
          "？",
          "，",
          "、",
          " ",
          ""
      ]
    )
    return text_splitter.split_text(text)

def build_chunks(pages: list[dict]) -> list[dict]:
    chunks = []
    for page in pages:
        page_chunks = split_recursive_by_token(page["text"], chunk_size=800, overlap=100)
        for chunk_index,chunk_text in enumerate(page_chunks):
            chunks.append({
                "chunk_id":f"dell-inspiron-16-{page['page_number']}-chunk-{chunk_index + 1}",
                "page_number":page["page_number"],
                "text":chunk_text
            })
    return chunks

all_chunks = build_chunks(pages)
print("总 chunk 数:", len(all_chunks))
for chunk in all_chunks:
    assert chunk["chunk_id"]
    assert chunk["page_number"] >= 1
    assert chunk["text"].strip()


with output_path.open("w", encoding="utf-8") as file:
    json.dump(
        all_chunks,
        file,
        ensure_ascii=False,
        indent=2
    )

print("已保存:", output_path)
print("保存数量:", len(all_chunks))

print("空文本数量:", sum(
    1 for chunk in all_chunks
    if not chunk["text"].strip()
))
encoding = tiktoken.get_encoding("cl100k_base")

max_chunk = max(
    all_chunks,
    key=lambda chunk: len(encoding.encode(chunk["text"]))
)

max_token_count = len(
    encoding.encode(max_chunk["text"])
)

print("最长 chunk 字符数:", len(max_chunk["text"]))
print("最长 chunk token 数:", max_token_count)
print("来源页码:", max_chunk["page_number"])