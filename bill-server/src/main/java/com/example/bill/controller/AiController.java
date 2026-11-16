package com.example.bill.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.bill.common.AiParseRequest;
import com.example.bill.common.Result;
import com.example.bill.entity.Bill;
import com.example.bill.entity.Category;
import com.example.bill.service.BillService;
import com.example.bill.service.CategoryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * AI 账单解析接口
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    /** Python 抽取服务地址（注意路径为 /api/extract） */
    private static final String AI_EXTRACT_URL = "http://127.0.0.1:8000/api/extract";

    private static final String AI_UNAVAILABLE = "AI 服务暂不可用，请手动记账";

    private final RestTemplate restTemplate;
    private final BillService billService;
    private final CategoryService categoryService;

    public AiController(RestTemplate restTemplate, BillService billService, CategoryService categoryService) {
        this.restTemplate = restTemplate;
        this.billService = billService;
        this.categoryService = categoryService;
    }

    /**
     * 解析自然语言并直接入库
     */
    @PostMapping("/parse")
    public Result<Bill> parse(@RequestBody AiParseRequest request) {
        if (request.getText() == null || request.getText().isBlank()) {
            return Result.fail("text 不能为空");
        }
        JsonNode root = callExtract(request.getText());
        if (root == null) {
            return Result.fail(AI_UNAVAILABLE);
        }
        if (root.path("code").asInt(-1) != 0) {
            String msg = root.path("msg").asText("");
            return Result.fail(msg.isBlank() ? AI_UNAVAILABLE : msg);
        }
        return saveParsedBill(root.path("data"), request.getText());
    }

    /**
     * 调用 Python 抽取服务，超时或异常统一返回 null
     */
    private JsonNode callExtract(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("text", text), headers);
            return restTemplate.postForObject(AI_EXTRACT_URL, entity, JsonNode.class);
        } catch (Exception e) {
            log.warn("调用 AI 服务失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 校验 AI 返回数据、补齐分类 ID 并入库
     */
    private Result<Bill> saveParsedBill(JsonNode data, String rawText) {
        String categoryName = data.path("category_name").asText("").trim();
        if (categoryName.isEmpty() || data.path("amount").isMissingNode()) {
            return Result.fail("AI 返回数据不完整，请手动记账");
        }
        Category category = categoryService.getOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, categoryName), false);
        if (category == null) {
            return Result.fail("分类不存在：" + categoryName);
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(data.path("amount").asText());
        } catch (NumberFormatException e) {
            return Result.fail("AI 返回金额格式错误，请手动记账");
        }
        Bill bill = new Bill();
        bill.setAmount(amount);
        bill.setCategoryId(category.getId());
        bill.setCategoryName(categoryName);
        bill.setBillType(resolveBillType(data.path("bill_type").asText("")));
        bill.setBillDate(parseDate(data.path("bill_date")));
        bill.setNote(data.path("note").asText(""));
        bill.setSourceType("AI");
        bill.setAiRaw(rawText);
        billService.save(bill);
        return Result.ok(bill);
    }

    /**
     * 校验收支类型，非法值回退为支出
     */
    private String resolveBillType(String value) {
        return "INCOME".equalsIgnoreCase(value) ? "INCOME" : "EXPENSE";
    }

    /**
     * 解析日期，格式异常时回退为今天
     */
    private LocalDate parseDate(JsonNode node) {
        try {
            return LocalDate.parse(node.asText());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }
}
