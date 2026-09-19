package com.moviebooking.pricing;

import com.moviebooking.domain.SeatType;

import java.math.BigDecimal;
import java.util.Map;

public final class FixedSeatPricing implements PricingService {
    private final Map<SeatType, BigDecimal> prices;

    public FixedSeatPricing(Map<SeatType, BigDecimal> prices) {
        this.prices = Map.copyOf(prices);
    }

    public BigDecimal priceFor(SeatType type) {
        return prices.get(type);
    }
}
