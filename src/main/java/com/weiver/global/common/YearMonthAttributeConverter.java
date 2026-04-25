package com.weiver.global.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;
import java.time.YearMonth;

@Converter(autoApply = true)
public class YearMonthAttributeConverter implements AttributeConverter<YearMonth, LocalDate> {

    @Override
    public LocalDate convertToDatabaseColumn(YearMonth attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.atDay(1);
    }

    @Override
    public YearMonth convertToEntityAttribute(LocalDate dbData) {
        if (dbData == null) {
            return null;
        }
        return YearMonth.from(dbData);
    }
}