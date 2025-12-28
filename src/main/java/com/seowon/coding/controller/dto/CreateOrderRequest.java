package com.seowon.coding.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank
        String customerName,

        @NotBlank
        @Email
        String customerEmail,

        @Valid
        @NotEmpty
        List<OrderProduct> products
) {
}
