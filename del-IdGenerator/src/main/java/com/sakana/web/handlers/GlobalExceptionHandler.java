package com.sakana.web.handlers;


import com.sakana.exceptions.NetWorkException;
import com.sakana.web.vo.R;
import com.sakana.web.vo.ResultCode;
import lombok.extern.java.Log;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

//项目的异常处理中心，设计异常处理的逻辑
//对于业务方法抛出的异常捕获处理，我们通过AOP增强的方式进行
//平时一般使用的时try catch 处理，但是这样不优雅，可读性低，且对于每一个业务方法都需要添加一个try catch子块
@RestControllerAdvice
@Log
public class GlobalExceptionHandler {


    @ExceptionHandler(NetWorkException.class)
    public R<?> networkError(NetWorkException e) {
        return R.fail(
                ResultCode.NETWORK_ERROR.getCode(),
                e.getMessage());
    }


    //异常处理的兜底逻辑，用于处理没有针对的异常
    @ExceptionHandler(Exception.class)
    public R<?> exception(Exception e) {
        return R.fail(
                500,
                "internal server error"
        );
    }

}
