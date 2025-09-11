package org.apache.fineract.portfolio.loanaccount.rescheduleloan.data;

public enum ChargeHandlingOption {
    WAIVE("WAIVE"),
    CARRY("CARRY");

    private final String value;

    ChargeHandlingOption(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static ChargeHandlingOption fromString(String value) {
        if (value == null) {
            return null;
        }
        for (ChargeHandlingOption option : ChargeHandlingOption.values()) {
            if (option.value.equalsIgnoreCase(value)) {
                return option;
            }
        }
        throw new IllegalArgumentException("Invalid charge handling option: " + value);
    }
}
