package com.tms.common.exception;

import com.tms.common.api.ResultCode;

/**
 * 业务异常：可被识别、可被全局异常处理器翻译成明确错误码的异常。
 *
 * <p>只用于<b>命令类接口的业务拒绝</b>。事实类接口不得用它表达"不同意"——
 * 已经发生的事实不可拒绝（[06-api-contracts] §2）。
 */
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.message());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message, Throwable cause) {
        super(message, cause);
        this.resultCode = resultCode;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }
}
