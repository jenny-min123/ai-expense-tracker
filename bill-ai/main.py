"""bill-ai：自然语言账单抽取服务（FastAPI + DeepSeek）"""

import json
import logging
import os
from datetime import date, timedelta

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from openai import OpenAI
from pydantic import BaseModel, Field

APP_NAME = "bill-ai"
DEFAULT_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-flash"
CATEGORIES = ["餐饮", "交通", "购物", "住房", "医疗", "娱乐", "通讯", "教育", "其他"]

logger = logging.getLogger(APP_NAME)
logging.basicConfig(level=logging.INFO)

app = FastAPI(title=APP_NAME)


class ExtractRequest(BaseModel):
    """抽取请求"""

    text: str = Field(..., min_length=1, description="自然语言账单描述")


def ok(data):
    """成功响应（与后端 Result 结构保持一致）"""
    return {"code": 0, "msg": "成功", "data": data}


def fail(msg):
    """失败响应"""
    return {"code": 1, "msg": msg, "data": None}


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """参数校验失败也返回统一结构"""
    return JSONResponse(status_code=200, content=fail("参数错误：text 不能为空"))


def build_prompt() -> str:
    """构造抽取 Prompt，日期按调用当天计算"""
    today = date.today()
    yesterday = today - timedelta(days=1)
    return (
        "你是一个记账信息抽取器。请从用户的一句话中抽取账单信息，"
        "只输出一个 JSON 对象，不要输出解释、Markdown 代码块或任何多余文字。\n"
        "JSON 字段要求：\n"
        "1. amount：数字（单位：元），支持“25块5”这类口语，例如 25.5；\n"
        "2. category_name：必须从以下 9 个分类中精准选择一个："
        + "、".join(CATEGORIES)
        + "；\n"
        f"3. bill_date：日期，格式 YYYY-MM-DD；今天是 {today.isoformat()}，"
        f"昨天是 {yesterday.isoformat()}，“今天”请换算为 {today.isoformat()}，"
        f"用户未提及时也使用 {today.isoformat()}；\n"
        "4. note：简短备注，最多 20 个字。\n"
        "示例输出：\n"
        f'{{"amount": 25.5, "category_name": "交通", "bill_date": "{today.isoformat()}", '
        '"note": "打车"}'
    )


def parse_json(content: str) -> dict:
    """从模型输出中解析 JSON，兼容 ```json 代码块"""
    text = (content or "").strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[-1]
        text = text.rsplit("```", 1)[0]
    start, end = text.find("{"), text.rfind("}")
    if start == -1 or end == -1 or end < start:
        raise ValueError("模型未返回 JSON")
    return json.loads(text[start:end + 1])


def normalize(raw: dict) -> dict:
    """校验并规范化模型返回的字段"""
    amount = float(str(raw.get("amount", "")).replace("元", "").strip())
    if amount < 0:
        raise ValueError("金额不能为负数")
    category = str(raw.get("category_name", "")).strip()
    if category not in CATEGORIES:
        category = "其他"
    try:
        bill_date = date.fromisoformat(str(raw.get("bill_date", "")).strip())
    except ValueError:
        bill_date = date.today()
    note = str(raw.get("note", "")).strip()[:20]
    return {
        "amount": round(amount, 2),
        "category_name": category,
        "bill_date": bill_date.isoformat(),
        "note": note,
    }


@app.post("/api/extract")
def extract(req: ExtractRequest):
    """把自然语言解析为结构化账单"""
    api_key = os.getenv("DEEPSEEK_API_KEY")
    if not api_key:
        return fail("未配置 DEEPSEEK_API_KEY 环境变量")

    client = OpenAI(
        api_key=api_key,
        base_url=os.getenv("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL),
        timeout=30.0,
    )
    try:
        completion = client.chat.completions.create(
            model=os.getenv("DEEPSEEK_MODEL", DEFAULT_MODEL),
            messages=[
                {"role": "system", "content": build_prompt()},
                {"role": "user", "content": req.text},
            ],
            temperature=0,
        )
        content = completion.choices[0].message.content
        data = normalize(parse_json(content))
        logger.info("extract ok: %s -> %s", req.text, data)
        return ok(data)
    except Exception as exc:  # 外部服务或解析异常统一转为业务失败
        logger.exception("extract failed")
        return fail(f"AI 解析失败：{exc}")
