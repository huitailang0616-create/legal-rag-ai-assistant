package com.ailegal.exception;

/**
 * 5B 文书智能生成失败异常（与 4B ExtractionException 同构）。
 *
 * 扩展 BusinessException（code=500，GlobalExceptionHandler 子类分支自动兜住，HTTP 500 统一契约），
 * 额外携带短错误码用于操作日志（"失败: RATE_LIMIT" 等）——不记录任何案件内容/prompt/raw 响应。
 * 失败原子性由外层保证：任何本异常都发生在 document_draft UPDATE 之前，旧 content/status 原值不变。
 */
public class DocumentGenerationException extends BusinessException {

    /** LLM 限流(429)：瞬时，提示稍后重试 */
    public static final String RATE_LIMIT = "RATE_LIMIT";
    /** Provider 返回非 2xx 或调用异常 */
    public static final String PROVIDER_ERROR = "PROVIDER_ERROR";
    /** 读/连超时 */
    public static final String TIMEOUT = "TIMEOUT";
    /** 响应为空 / choices 空 / content 空 */
    public static final String EMPTY_RESPONSE = "EMPTY_RESPONSE";
    /** 非合法 JSON / 结构 / 类型 / 长度 / 额外字段不符 */
    public static final String INVALID_JSON = "INVALID_JSON";

    private final String errorCode;

    public DocumentGenerationException(String errorCode, String message) {
        super(500, message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
