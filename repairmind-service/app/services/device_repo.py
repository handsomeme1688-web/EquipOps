"""设备 / 维修历史数据访问层（工具调用的后端数据）。

对应手册 P2 · Day32/33：工具调用必须由服务端做权限与数据隔离，
绝不只信前端传的参数。这里以本地样例数据模拟 EquipOps Java 侧的
设备与工单接口；生产环境中该层会替换为调用 Java API（带 JWT 用户上下文）。

核心规则：
- query_device / query_repair_history 只允许返回 requester 所属 dept 的数据；
- 跨部门访问直接拒绝，返回无权限提示。
"""
from __future__ import annotations

import json
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
DATA_FILE = PROJECT_ROOT / "data" / "device_repairs.json"

_LOADED: dict | None = None


def _load() -> dict:
    global _LOADED
    if _LOADED is None:
        with DATA_FILE.open("r", encoding="utf-8") as f:
            _LOADED = json.load(f)
    return _LOADED


def query_device(keyword: str, dept_id: str) -> dict:
    """按关键字查设备，仅返回本部门设备。跨部门一律查不到。"""
    kw = (keyword or "").strip().lower()
    data = _load()
    result = []
    for d in data["devices"]:
        if d["dept_id"] != dept_id:
            continue  # 数据隔离第一道闸：直接跳过非本部门设备
        haystack = " ".join([d["device_id"], d["name"], d["model"]]).lower()
        if not kw or kw in haystack:
            result.append(d)
    return {"devices": result, "scope": dept_id}


def query_repair_history(device_id: str, dept_id: str) -> dict:
    """查某台设备的维修历史。设备不属于本部门 → 明确拒绝。"""
    device = get_device(device_id, dept_id)
    if device.get("denied"):
        return {"records": [], "error": device.get("error", "无权限"), "denied": True}
    if device.get("error"):
        return {"records": [], "error": device["error"]}
    data = _load()
    records = [r for r in data["repair_history"] if r["device_id"] == device_id]
    return {"records": records, "device": device["name"]}


def get_device(device_id: str, dept_id: str) -> dict:
    """按 ID 查设备，带部门隔离。跨部门访问返回 denied。"""
    data = _load()
    device = next((d for d in data["devices"] if d["device_id"] == device_id), None)
    if device is None:
        return {"error": f"设备 {device_id} 不存在"}
    if device["dept_id"] != dept_id:
        return {"denied": True, "error": f"无权限访问设备 {device_id}（不属于当前部门）"}
    return device
