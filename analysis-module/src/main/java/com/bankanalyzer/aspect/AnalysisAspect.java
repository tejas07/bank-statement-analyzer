package com.bankanalyzer.aspect;

import com.bankanalyzer.api.dto.SummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class AnalysisAspect {

    // --- Pointcuts ---

    @Pointcut("within(com.bankanalyzer.service..*)")
    private void allServiceMethods() {
    }

    @Pointcut("execution(* com.bankanalyzer.api.AnalyzeController.getSummary(..))" +
            " || execution(* com.bankanalyzer.api.AnalyzeController.getMultiSummary(..))")
    private void summaryEndpoints() {
    }

    // --- Execution timing: all service layer methods ---

    @Around("allServiceMethods()")
    public Object logExecutionTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = pjp.proceed();
        long elapsed = System.currentTimeMillis() - start;

        String className = pjp.getSignature().getDeclaringType().getSimpleName();
        String methodName = pjp.getSignature().getName();

        if (elapsed > 500) {
            log.warn("SLOW [{}ms] {}.{}()", elapsed, className, methodName);
        } else {
            log.debug("PERF [{}ms] {}.{}()", elapsed, className, methodName);
        }

        return result;
    }

    // --- Audit trail: every successful summary analysis ---

    @AfterReturning(pointcut = "summaryEndpoints()", returning = "response")
    public void auditAnalysis(Object response) {
        SummaryResponse summary = extractSummary(response);
        if (summary == null) return;

        String bank = summary.getDetectedBank() != null
                ? summary.getDetectedBank()
                : (summary.getDetectedBanks() != null ? String.join(", ", summary.getDetectedBanks()) : "Unknown");

        log.info("AUDIT | bank={} | type={} | transactions={} | debit={} | credit={}",
                bank,
                summary.getStatementType(),
                summary.getTotalTransactions(),
                String.format("%.2f", summary.getTotalDebit()),
                String.format("%.2f", summary.getTotalCredit())
        );
    }

    @SuppressWarnings("unchecked")
    private SummaryResponse extractSummary(Object response) {
        if (response instanceof SummaryResponse s) return s;
        if (response instanceof ResponseEntity<?> re && re.getBody() instanceof SummaryResponse s) return s;
        return null;
    }
}
