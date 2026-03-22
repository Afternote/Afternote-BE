package com.afternote.domain.afternote.service.validation;

import com.afternote.domain.afternote.model.AfternoteCategoryType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AfternoteValidationStrategyFactory {

    private final List<AfternoteCategoryValidationStrategy> strategies;
    private final Map<AfternoteCategoryType, AfternoteCategoryValidationStrategy> strategyMap =
            new EnumMap<>(AfternoteCategoryType.class);

    @PostConstruct
    void init() {
        for (AfternoteCategoryValidationStrategy strategy : strategies) {
            if (strategyMap.containsKey(strategy.category())) {
                throw new IllegalStateException("Duplicate validation strategy for category: " + strategy.category());
            }
            strategyMap.put(strategy.category(), strategy);
        }
    }

    public AfternoteCategoryValidationStrategy get(AfternoteCategoryType categoryType) {
        AfternoteCategoryValidationStrategy strategy = strategyMap.get(categoryType);
        if (strategy == null) {
            throw new IllegalStateException("No validation strategy for category: " + categoryType);
        }
        return strategy;
    }
}
