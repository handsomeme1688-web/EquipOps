"""下载 BGE-Reranker 模型（约 1GB，对应手册 Day29 重排）。

下载完成后，retriever.search(..., mode="hybrid_rerank") 会自动生效；
未下载时该模式优雅回退到 hybrid。

用法：conda activate repairmind && python scripts/download_reranker.py
下载慢可先设 HF_ENDPOINT=https://hf-mirror.com
"""
from sentence_transformers import CrossEncoder

from app.core.config import settings

if __name__ == "__main__":
    print(f"加载/下载 {settings.rerank_model} ...（首次约 1GB，请耐心）")
    model = CrossEncoder(settings.rerank_model, device=settings.rerank_device)
    score = float(model.predict([("怎么拆内存条", "步骤 1. 提起聚脂薄膜以触及内存模块。")])[0])
    print(f"OK，重排已就绪。示例相关度得分：{score:.4f}")
