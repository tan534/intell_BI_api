package com.intell_BI_backend.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.intell_BI_backend.annotation.AuthCheck;
import com.intell_BI_backend.common.BaseResponse;
import com.intell_BI_backend.common.DeleteRequest;
import com.intell_BI_backend.common.ErrorCode;
import com.intell_BI_backend.common.ResultUtils;
import com.intell_BI_backend.constant.CommonConstant;
import com.intell_BI_backend.constant.UserConstant;
import com.intell_BI_backend.exception.BusinessException;
import com.intell_BI_backend.exception.ThrowUtils;
import com.intell_BI_backend.model.dto.chart.*;
import com.intell_BI_backend.model.entity.Chart;
import com.intell_BI_backend.model.entity.User;
import com.intell_BI_backend.service.ChartService;
import com.intell_BI_backend.service.DeepSeekService;
import com.intell_BI_backend.service.UserService;
import com.intell_BI_backend.utils.ExcelUtils;
import com.intell_BI_backend.utils.SqlUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 图表接口
 *
 */
@RestController
@RequestMapping("/chart")
@Slf4j
@Api(tags = "图表管理")
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private DeepSeekService deepSeekService;

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @ApiOperation("删除图表")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get/{id}")
    @ApiOperation("根据 ID 获取图表")
    public BaseResponse<Chart> getChartById(@PathVariable Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（仅管理员）
     *
     * @param chartQueryRequest
     * @return
     */
    @PostMapping("/list/page")

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }


    /**
     * 更新
     *
     * @param chartParams
     * @param request
     * @return
     */
    @ApiOperation("更改图表接口")
    @PostMapping("/update")
    public BaseResponse<Chart> updateChart(
            @RequestBody ChartUpdateRequest chartParams,
            HttpServletRequest request
    ) {
        // 1. 获取登录用户
        User loginUser = userService.getLoginUser(request);

        // 2. 校验参数
        ThrowUtils.throwIf(chartParams.getId() == null, ErrorCode.PARAMS_ERROR, "图表ID不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(chartParams.getGoal()), ErrorCode.PARAMS_ERROR, "分析目标不能为空");

        // 3. 查询旧图表
        Chart oldChart = chartService.getById(chartParams.getId());
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR, "图表不存在");

        // 4. 仅本人可修改
        if (!oldChart.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你没有权限修改此图表");
        }

        // 5. 重新构建Prompt
        String goal = chartParams.getGoal();
        String chartType = chartParams.getChartType();
        String csvData = oldChart.getChartData(); // 保留原数据

        String prompt = "分析目标：" + goal + "\n"
                + "图表类型：" + chartType + "\n"
                + "数据如下：\n" + csvData;

        // 6. 调用AI重新生成
        JSONObject aiResult = deepSeekService.analyzeCsv(prompt);
        ThrowUtils.throwIf(aiResult == null, ErrorCode.SYSTEM_ERROR, "AI分析失败");

        // 7. 清洗图表配置
        String genChartJs = aiResult.getString("genChart");
        String genChartStr = cleanEchartsJsToJson(genChartJs);
        String genResult = aiResult.getString("genResult");

        // 8. 构建更新对象
        Chart updateChart = new Chart();
        updateChart.setId(oldChart.getId());
        updateChart.setGoal(goal);
        updateChart.setChartName(chartParams.getChartName());
        updateChart.setChartType(chartType);
        updateChart.setGenChart(genChartStr);
        updateChart.setGenResult(genResult);

        // 9. 执行更新
        boolean success = chartService.updateById(updateChart);
        ThrowUtils.throwIf(!success, ErrorCode.OPERATION_ERROR, "图表更新失败");

        return ResultUtils.success(updateChart);
    }

    /**
     * 获取查询包装类
     *
     * @param
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }

        Long id = chartQueryRequest.getId();
        String chartName = chartQueryRequest.getChartName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.like(StringUtils.isNotBlank(chartName), "chartName", chartName);
        queryWrapper.like(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(userId != null && userId > 0, "userId", userId);
        queryWrapper.eq("isDelete",false);

        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }



    /**
     * 获取用户上传文件并 AI 智能分析 → 直接返回 ECharts 可使用的 JSON
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @return
     */
    @PostMapping("/gen")
    @ApiOperation("AI 图表分析接口")
    public BaseResponse<JSONObject> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest,
                                                 HttpServletRequest request) {
        // 校验文件格式
        String filename = multipartFile.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传失败,仅支持.xlsx和.xls格式文件,请重新上传！");
        }

        //获取参数
        User loginUser = userService.getLoginUser(request);
        String chartName = genChartByAiRequest.getChartName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();
        //校验参数
        ThrowUtils.throwIf(StringUtils.isBlank(chartName), ErrorCode.PARAMS_ERROR, "图表名称不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "分析目标不能为空");
        ThrowUtils.throwIf(StringUtils.isBlank(chartType), ErrorCode.PARAMS_ERROR, "图表类型不能为空");
        //Excel转CSV字符串
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        //拼接给AI的完整指令
        String prompt = "分析目标：" + goal + "\n"
                + "图表类型：" + chartType + "\n"
                + "数据如下：\n" + csvData;
        //调用 DeepSeek分析返回JSON
        JSONObject aiResult = deepSeekService.analyzeCsv(prompt);

        //分离生成的图表和分析结论
        String genChart= aiResult.getString("genChart");
        String genChartStr = cleanEchartsJsToJson(genChart);
        String genResult = aiResult.getString("genResult");

        //将图表信息插入数据库
        Chart chart = new Chart();
        chart.setChartName(chartName);
        chart.setChartType(chartType);
        chart.setGoal(goal);
        chart.setUserId(loginUser.getId());
        chart.setChartData(csvData);
        chart.setGenChart(genChartStr);
        chart.setGenResult(genResult);
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图表信息保存失败！");

        JSONObject responseData = new JSONObject();
        responseData.put("genChartStr", genChartStr);
        responseData.put("genResult", genResult);

        return ResultUtils.success(responseData);
    }
    private String cleanEchartsJsToJson(String echartsJs) {
        if (StringUtils.isBlank(echartsJs)) {
            return "{}"; // 空值返回空JSON对象
        }
        // 步骤1：去掉 "option = " 赋值语句
        String jsonStr = echartsJs.replaceAll("option\\s*=\\s*", "");
        // 步骤2：去掉末尾的分号/逗号
        jsonStr = jsonStr.replaceAll(";$", "").replaceAll(",\\s*}$", "}");
        // 步骤3：单引号转双引号（JSON要求双引号）
        jsonStr = jsonStr.replace("'", "\"");
        // 步骤4：去掉多余的换行/空格（可选，提升稳定性）
        jsonStr = jsonStr.replaceAll("\\n|\\r", "").trim();
        // 步骤5：兜底（确保是合法JSON）
        try {
            JSONObject.parseObject(jsonStr); // 验证JSON合法性
            return jsonStr;
        } catch (Exception e) {
            return "{}"; // 解析失败返回空JSON
        }
    }
}
