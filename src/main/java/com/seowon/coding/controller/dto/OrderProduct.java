package com.seowon.coding.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderProduct(

        @NotNull
        Long productId,

        @Min(1)
        @NotNull
        Integer quantity
) {
}
