package com.bankanalyzer.validation;

import org.springframework.stereotype.Component;

@Component
public class ForecastParamsValidator implements Validator<ForecastParams> {

    @Override
    public void validate(ForecastParams params) {
        if (params.months() < 1 || params.months() > 24) {
            throw new IllegalArgumentException("'months' must be between 1 and 24.");
        }
        if (params.inflationRate() < 0 || params.inflationRate() > 50) {
            throw new IllegalArgumentException("'inflationRate' must be between 0 and 50 (%).");
        }
    }
}
