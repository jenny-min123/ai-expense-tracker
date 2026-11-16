package com.example.bill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 账单分类
 */
@Data
@TableName("t_category")
public class Category {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 分类类型：EXPENSE-支出分类，INCOME-收入分类 */
    private String type;

    /** 分类图标（emoji 或图片地址） */
    private String icon;

    /** 排序值，越小越靠前 */
    private Integer sort;
}
