package com.ledgerxlite.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Logging aspect for comprehensive method execution logging.
 * 
 * Automatically logs:
 * - Method entry with parameters
 * - Method exit with return value
 * - Execution time
 * - Exceptions with full stack trace
 * 
 * Applied to all service and controller methods.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Log all service method calls with parameters and execution time.
     */
    @Around("execution(* com.ledgerxlite.service..*(..))")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        Object[] args = joinPoint.getArgs();
        
        // Generate unique execution ID for this method call
        String executionId = generateExecutionId();
        MDC.put("executionId", executionId);
        
        // Log method entry
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║ SERVICE CALL: {}.{}", className, methodName);
        log.info("║ Execution ID: {}", executionId);
        log.info("║ Thread: {}", Thread.currentThread().getName());
        
        // Log parameters
        if (args != null && args.length > 0) {
            log.info("║ Parameters:");
            String[] paramNames = signature.getParameterNames();
            for (int i = 0; i < args.length; i++) {
                String paramName = (paramNames != null && i < paramNames.length) ? paramNames[i] : "arg" + i;
                String paramValue = formatParameter(args[i]);
                log.info("║   - {}: {}", paramName, paramValue);
            }
        } else {
            log.info("║ Parameters: (none)");
        }
        log.info("╠══════════════════════════════════════════════════════════════");
        
        long startTime = System.currentTimeMillis();
        Object result = null;
        boolean success = false;
        
        try {
            // Execute the actual method
            result = joinPoint.proceed();
            success = true;
            return result;
            
        } catch (Exception e) {
            log.error("╠══════════════════════════════════════════════════════════════");
            log.error("║ ✗ EXCEPTION THROWN");
            log.error("║ Exception Type: {}", e.getClass().getSimpleName());
            log.error("║ Exception Message: {}", e.getMessage());
            log.error("╚══════════════════════════════════════════════════════════════");
            throw e;
            
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (success) {
                log.info("╠══════════════════════════════════════════════════════════════");
                log.info("║ ✓ SUCCESS");
                log.info("║ Return Value: {}", formatParameter(result));
                log.info("║ Execution Time: {} ms", executionTime);
                log.info("╚══════════════════════════════════════════════════════════════");
            }
            
            // Performance warning for slow operations
            if (executionTime > 1000) {
                log.warn("⚠ SLOW OPERATION: {}.{} took {} ms", className, methodName, executionTime);
            }
            
            MDC.remove("executionId");
        }
    }

    /**
     * Log all controller method calls (lighter logging than services).
     */
    @Around("execution(* com.ledgerxlite.controller..*(..))")
    public Object logControllerMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        
        log.info("→ HTTP REQUEST: {}.{}", className, methodName);
        
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("← HTTP RESPONSE: {}.{} completed in {} ms", className, methodName, executionTime);
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("← HTTP ERROR: {}.{} failed after {} ms - {}: {}", 
                     className, methodName, executionTime, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    /**
     * Log repository method calls for debugging database operations.
     */
    @Around("execution(* com.ledgerxlite.repository..*(..))")
    public Object logRepositoryMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        Object[] args = joinPoint.getArgs();
        
        // Only log at DEBUG level for repositories (can be verbose)
        if (log.isDebugEnabled()) {
            String params = args != null ? Arrays.stream(args)
                .map(this::formatParameter)
                .collect(Collectors.joining(", ")) : "";
            
            log.debug("DB CALL: {}.{}({})", className, methodName, params);
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            
            if (log.isDebugEnabled()) {
                log.debug("DB RETURN: {}.{} completed in {} ms", className, methodName, executionTime);
            }
            
            // Warn about slow queries
            if (executionTime > 500) {
                log.warn("⚠ SLOW QUERY: {}.{} took {} ms", className, methodName, executionTime);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("DB ERROR: {}.{} - {}: {}", className, methodName, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    /**
     * Format parameter for logging (truncate long values, mask sensitive data).
     */
    private String formatParameter(Object param) {
        if (param == null) {
            return "null";
        }
        
        String value = param.toString();
        
        // Mask potential sensitive data
        if (value.contains("password") || value.contains("token") || value.contains("secret")) {
            return "[REDACTED]";
        }
        
        // Truncate long values
        if (value.length() > 100) {
            return value.substring(0, 97) + "...";
        }
        
        return value;
    }

    /**
     * Generate unique execution ID for tracing.
     */
    private String generateExecutionId() {
        return String.format("%d-%d", System.currentTimeMillis(), Thread.currentThread().getId());
    }
}
