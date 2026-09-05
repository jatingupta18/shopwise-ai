package com.jatin.ai_shopping_agent.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseShoppingFallbackTest {
    private final DatabaseShoppingFallback fallback = new DatabaseShoppingFallback(null);

    @Test
    void modelNumbersAreNotRequestedComparisonCounts() {
        assertThat(fallback.requestedComparisonCount("Compare Redmi 5 with other available products"))
                .hasValue(2);
        assertThat(fallback.requestedComparisonCount("Compare Galaxy 8 with other available products"))
                .hasValue(2);
    }

    @Test
    void ramAndStorageFiguresAreNotRequestedComparisonCounts() {
        assertThat(fallback.requestedComparisonCount("compare 8GB RAM laptops"))
                .hasValue(2);
        assertThat(fallback.requestedComparisonCount("compare 8 GB RAM laptops"))
                .hasValue(2);
        assertThat(fallback.requestedComparisonCount("compare 16GB laptops with other available products"))
                .hasValue(2);
    }

    @Test
    void explicitProductCountsAreDetected() {
        assertThat(fallback.requestedComparisonCount("Compare the best 2 laptops under 60000"))
                .hasValue(2);
        assertThat(fallback.requestedComparisonCount("Compare 3 laptops under 60000"))
                .hasValue(3);
        assertThat(fallback.requestedComparisonCount("Top 5 laptops under 60000"))
                .hasValue(5);
        assertThat(fallback.requestedComparisonCount("compare two phones"))
                .hasValue(2);
    }

    @Test
    void compareWithoutACountImpliesTwo() {
        assertThat(fallback.requestedComparisonCount("Compare the best laptops under 60000"))
                .hasValue(2);
    }
}
