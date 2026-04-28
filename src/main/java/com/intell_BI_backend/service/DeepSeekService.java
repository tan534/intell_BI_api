package com.intell_BI_backend.service;

import com.alibaba.fastjson.JSONObject;

public interface DeepSeekService {

        /**
         * 调用DeepSeek分析CSV数据
         * @param csvData 输入的CSV字符串（如"日期,用户数\n1,10\n2,20\n3,30"）
         * @return 标准JSON分析结果（适配BI前端渲染）
         */
        JSONObject analyzeCsv(String csvData);
}
