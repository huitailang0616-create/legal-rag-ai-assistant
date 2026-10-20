package com.ailegal.exception;

/**
 * 4B 案件事实智能提取失败异常（4B）。
 *
 * 扩展 BusinessException（code=500，GlobalExceptionHandler 子类分支自动兜住，HTTP 500 统一契约），
 * 额外携带短错误码用于操作日志（"失败: RATE_LIMIT" 等）——不记录任何案件内容/prompt/raw 响应。
 */
public class ExtractionException extends BusinessException {

    /** LLM 限流(429)：瞬时，提示稍后重试 */
    public static final String RATE_LIMIT = "RATE_LIMIT";
    /** Provider 返回非 2xx 或调用异常 */
    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";
    /** 读/连超时 */
    public static final String TIMEOUT = "TIMEOUT";
    /** 响应为空 / choices 空 / content 空 */
    public static final String EMPTY_RESPONSE = "EMPTY_RESPONSE";
    /** 非合法 JSON / 结构 / 类型 / 长度 / 数量不符 */
    public static final String INVALID_JSON = "INVALID_JSON";

    private final String errorCode;

    public ExtractionException(String errorCode, String message) {
        super(500, message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
