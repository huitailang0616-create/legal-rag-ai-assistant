package com.ailegal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateRunRequest {

    @NotBlank(message = "批次名称不能为空")
    @Size(max = 100, message = "批次名称最长100字")
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
