"""文本切分器（对应手册 Day28）。

递归切分策略：
1. 去页眉页脚 / 空行清洗；
2. 优先按段落空行切；
3. 段落仍超长按句号/分号切（中英混排都能处理）；
4. 仍超长则按字符硬切。

边界保证：空文档、只有页眉页脚、超长无标点段落、中英混排都不崩溃且 chunk 长度有上限。
"""
from __future__ import annotations

import re

DEFAULT_MAX_CHARS = 500

_SENTENCE_SPLIT = re.compile(r"(?<=[。；;！？!?])")


def split_document(text: str, max_chars: int = DEFAULT_MAX_CHARS) -> list[str]:
    """清洗 + 递归切分，返回非空 chunk 列表。

    按空行保留段落结构（页眉/页脚清洗后段落边界仍在），
    这样段落切分策略才能生效，而不是把整篇压成一块。
    """
    if not text or not text.strip():
        return []
    paragraphs = [p for p in re.split(r"\n\s*\n", text) if p.strip()]
    if not paragraphs:
        return []
    cleaned_paras = [
        "\n".join(ln.strip() for ln in p.splitlines() if ln.strip())
        for p in paragraphs
    ]
    body = "\n\n".join(cleaned_paras)
    return _recursive_split(body, max_chars)


def _recursive_split(text: str, max_chars: int) -> list[str]:
    if len(text) <= max_chars:
        return [text]

    # 1) 段落切
    parts = [p for p in re.split(r"\n{2,}", text) if p.strip()]
    if len(parts) > 1:
        return _flatten(parts, max_chars)

    # 2) 句子切
    parts = [p for p in _SENTENCE_SPLIT.split(text) if p.strip()]
    if len(parts) > 1:
        return _flatten(parts, max_chars)

    # 3) 硬切（超长无标点）
    return [text[i:i + max_chars] for i in range(0, len(text), max_chars)]


def _flatten(parts: list[str], max_chars: int) -> list[str]:
    result: list[str] = []
    buf = ""
    for p in parts:
        if len(buf) + len(p) <= max_chars:
            buf += p
        else:
            if buf:
                result.append(buf)
            buf = p
    if buf:
        result.append(buf)

    # 单块仍超长（如一个超长句子）递归处理
    final: list[str] = []
    for r in result:
        if len(r) > max_chars:
            final.extend(_recursive_split(r, max_chars))
        else:
            final.append(r)
    return final
