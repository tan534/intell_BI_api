package com.intell_BI_backend.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

public class ExcelUtils {

    // ThreadLocal 复用 StringBuilder，减少 GC
    private static final ThreadLocal<StringBuilder> SB_CACHE = ThreadLocal.withInitial(() -> new StringBuilder(8192));

    /**
     * Excel 转 CSV 字符串（智能 BI 专用，包含表头！）
     * 优化点：
     * 1. 流式处理，避免内存溢出
     * 2. ThreadLocal 复用 StringBuilder
     * 3. 手动拼接代替 Stream，提升性能
     * 4. CSV 特殊字符转义
     *
     * @param file 上传的Excel文件
     * @return 包含表头的CSV字符串
     */
    public static String excelToCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 获取复用的 StringBuilder
        StringBuilder sb = SB_CACHE.get();
        sb.setLength(0); // 清空，复用

        final List<String> headers = new ArrayList<>();
        final boolean[] headerWritten = {false};

        try {
            // 使用 Map 类型避免类型转换错误
            AnalysisEventListener<Map<Integer, String>> listener = new AnalysisEventListener<Map<Integer, String>>() {

                @Override
                public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                    if (!headerWritten[0]) {
                        // 保存表头
                        headers.clear();
                        for (int i = 0; i < headMap.size(); i++) {
                            String header = headMap.get(i);
                            headers.add(header != null ? header : "");
                        }
                        // 写入 CSV 表头
                        writeCsvLine(sb, headers);
                        headerWritten[0] = true;
                    }
                }

                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    // 快速检查空行
                    boolean hasContent = false;
                    for (int i = 0; i < headers.size(); i++) {
                        String cell = data.get(i);
                        if (cell != null && !cell.trim().isEmpty()) {
                            hasContent = true;
                            break;
                        }
                    }

                    if (!hasContent) return;

                    // 手动拼接数据行
                    for (int i = 0; i < headers.size(); i++) {
                        if (i > 0) sb.append(',');
                        String cell = data.get(i);
                        if (cell != null && !cell.isEmpty()) {
                            appendEscapedCsv(sb, cell);
                        }
                    }
                    sb.append('\n');
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {}
            };

            // 执行读取
            EasyExcel.read(file.getInputStream(), listener)
                    .sheet(0)
                    .headRowNumber(1)  // 第一行作为表头
                    .doRead();

            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("Excel解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 写入 CSV 行
     */
    private static void writeCsvLine(StringBuilder sb, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            String field = fields.get(i);
            if (field != null && !field.isEmpty()) {
                appendEscapedCsv(sb, field);
            }
        }
        sb.append('\n');
    }

    /**
     * 追加转义后的 CSV 字段
     */
    private static void appendEscapedCsv(StringBuilder sb, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        // 检查是否需要转义
        boolean needEscape = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') {
                needEscape = true;
                break;
            }
        }

        if (needEscape) {
            sb.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '"') {
                    sb.append('"').append('"');
                } else {
                    sb.append(c);
                }
            }
            sb.append('"');
        } else {
            sb.append(value);
        }
    }
}