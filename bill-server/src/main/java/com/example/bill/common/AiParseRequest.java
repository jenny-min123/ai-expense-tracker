package com.example.bill.common;

import lombok.Data;

/**
 * AI 解析请求
 */
@Data
public class AiParseRequest {

    /** 自然语言账单描述 */
    private String text;
}
