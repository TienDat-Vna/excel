package com.example.excelimport.dto;

public record CustomerData(
        Long id,
        String name,
        String email,
        String phone
) {
}
