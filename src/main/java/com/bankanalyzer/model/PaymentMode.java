package com.bankanalyzer.model;

public enum PaymentMode {
    UPI,
    NEFT,
    RTGS,
    IMPS,
    ATM,
    CARD_POS,
    CHEQUE,
    ECS_NACH,
    OTHER;

    public String getLabel() {
        switch (this) {
            case CARD_POS: return "Card/POS";
            case ECS_NACH: return "ECS/NACH";
            default: return name();
        }
    }
}
