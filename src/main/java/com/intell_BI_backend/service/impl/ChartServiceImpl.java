package com.intell_BI_backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.intell_BI_backend.model.entity.Chart;
import com.intell_BI_backend.service.ChartService;
import com.intell_BI_backend.mapper.ChartMapper;
import org.springframework.stereotype.Service;

/**
* @author 35516
* @description 针对表【chart(图表信息)】的数据库操作Service实现
* @createDate 2026-04-02 19:05:14
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{

}




