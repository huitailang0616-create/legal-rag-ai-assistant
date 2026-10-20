package com.ailegal.dto;

import jakarta.validation.constraints.NotBlank;

public class RagRetrieveTestRequest {

    @NotBlank(message = "问题不能为空")
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
