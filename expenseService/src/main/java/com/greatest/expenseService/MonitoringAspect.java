package com.greatest.expenseService;

import lombok.extern.apachecommons.CommonsLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MonitoringAspect {
    @Around("@annotation(LogExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        System.out.println(joinPoint.getSignature() + " Started at: " + start + " ms");
        Object proceed= joinPoint.proceed();
        long executionTime = System.currentTimeMillis() -start;

        System.out.println(joinPoint.getSignature() + " executed in " + executionTime + " ms");
        return proceed;
    }
}
