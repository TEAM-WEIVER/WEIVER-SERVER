package com.weiver.global.logging.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ServiceLoggingAspect {

    private static final long SLOW_METHOD_THRESHOLD_MS = 1_000L;

    @Around("within(com.weiver..*Service) || within(com.weiver..*ServiceImpl)")
    public Object logServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        if(log.isDebugEnabled()) {
            log.debug("[Service Start] {}.{}", className, methodName);
        }

        Object result = joinPoint.proceed();

        long duration = System.currentTimeMillis() - startTime;

        if(duration >= SLOW_METHOD_THRESHOLD_MS) {
            log.warn("[Slow Service] {}.{} duration={}ms", className, methodName, duration);
        }

        if(log.isDebugEnabled()) {
            log.debug("[Service End] {}.{} duration={}ms", className, methodName, duration);
        }

        return result;
    }
}
