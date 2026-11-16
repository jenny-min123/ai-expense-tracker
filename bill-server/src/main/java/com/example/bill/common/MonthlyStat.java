package com.example.bill.common;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 月度收支统计
 */
@Data
public class MonthlyStat {

    /** 月份，格式 yyyy-MM */
    private String month;

    /** 收入合计 */
    private BigDecimal income = BigDecimal.ZERO;

    /** 支出合计 */
    private BigDecimal expense = BigDecimal.ZERO;

    /** 结余（收入 - 支出） */
    private BigDecimal net = BigDecimal.ZERO;
}
