package io.kontur.insightsapi.service.metric;

import io.kontur.insightsapi.service.SlowQueryDetection;
import org.apache.commons.lang3.time.StopWatch;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SlowQueryDetectionAspect {

    private final Logger logger = LoggerFactory.getLogger(SlowQueryDetectionAspect.class);

    @Value("${database.request-duration.yellow-zone}")
    private Long yellowZoneDuration;

    @Value("${database.request-duration.orange-zone}")
    private Long orangeZoneDuration;

    @Value("${database.request-duration.red-zone}")
    private Long redZoneDuration;

    @Around("@annotation(io.kontur.insightsapi.service.SlowQueryDetection)")
    public Object detect(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch watch = new StopWatch();
        watch.start();
        Object result = joinPoint.proceed();
        watch.stop();
        long timeInMilliseconds = watch.getTime();

        Signature signature = joinPoint.getSignature();
        int argumentPosition = getArgumentPosition(signature);
        String geometry = (String) joinPoint.getArgs()[argumentPosition];

        writeIntoLog(timeInMilliseconds, signature, geometry);

        return result;
    }

    private int getArgumentPosition(Signature signature) {
        MethodSignature methodSignature = (MethodSignature) signature;
        String paramWithGeometry = methodSignature.getMethod().getAnnotation(SlowQueryDetection.class).paramWithGeometry();
        String[] parameterNames = methodSignature.getParameterNames();
        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(paramWithGeometry)) {
                return i;
            }
        }
        return 0;
    }

    private void writeIntoLog(long timeInMilliseconds, Signature signature, String geometry){
        if (timeInMilliseconds > yellowZoneDuration && timeInMilliseconds <= orangeZoneDuration && timeInMilliseconds <= redZoneDuration) {
            logger.warn(String.format("[slow_query, yellow_zone] Query duration: %s Query in method %s in yellow zone with geometry %s",
                    timeInMilliseconds, signature.getName(), geometry));
        }
        if (timeInMilliseconds > orangeZoneDuration && timeInMilliseconds <= redZoneDuration) {
            logger.warn(String.format("[slow_query, orange zone] Query duration: %s Query in method %s in orange zone with geometry %s",
                    timeInMilliseconds, signature.getName(), geometry));
        }
        if (timeInMilliseconds > redZoneDuration) {
            logger.warn(String.format("[slow_query, red zone] Query duration: %s Query in method %s in red zone with geometry %s",
                    timeInMilliseconds, signature.getName(), geometry));
        }
    }
}
