package com.ailegal.service;

import com.ailegal.dto.ProcessGuideResponse;

import java.util.List;

public interface ProcessGuideService {

    /** 返回三个正式领域的固定办事流程指引（顺序稳定：劳动争议、房屋租赁、消费者权益） */
    List<ProcessGuideResponse> listAll();
}
