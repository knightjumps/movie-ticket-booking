package com.moviebooking.pricing;

import com.moviebooking.domain.SeatType;

import java.math.BigDecimal;

public interface PricingService {
    BigDecimal priceFor(SeatType seatType);
}
