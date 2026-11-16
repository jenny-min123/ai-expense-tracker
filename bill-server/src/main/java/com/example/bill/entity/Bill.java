package com.example.bill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账单
 */
@Data
@TableName("t_bill")
public class Bill {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 金额（元） */
    private BigDecimal amount;

    /** 分类 ID */
    private Long categoryId;

    /** 分类名称（冗余存储，便于查询展示） */
    private String categoryName;

    /** 收支类型：EXPENSE-支出，INCOME-收入 */
    private String billType;

    /** 账单日期 */
    private LocalDate billDate;

    /** 备注 */
    private String note;

    /** 录入来源：MANUAL-手动录入，AI-AI 识别 */
    private String sourceType;

    /** AI 提取的原始文本 */
    private String aiRaw;

    /** 创建时间 */
    private LocalDateTime createTime;
}
