package com.afternote.domain.afternote.service.relation;

import com.afternote.domain.afternote.model.AfternoteCategoryType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AfternoteRelationStrategyFactory {

    private final List<AfternoteCategoryRelationStrategy> strategies;
    private final Map<AfternoteCategoryType, AfternoteCategoryRelationStrategy> strategyMap =
            new EnumMap<>(AfternoteCategoryType.class);

    @PostConstruct
    void init() {
        strategyMap.putAll(strategies.stream()
                .collect(Collectors.toMap(
                        AfternoteCategoryRelationStrategy::category,
                        Function.identity(),
                        (existing, duplicate) -> {
                            throw new IllegalStateException(
                                    "Duplicate relation strategy for category: " + duplicate.category()
                            );
                        },
                        () -> new EnumMap<>(AfternoteCategoryType.class)
                )));
    }

    public AfternoteCategoryRelationStrategy get(AfternoteCategoryType categoryType) {
        AfternoteCategoryRelationStrategy strategy = strategyMap.get(categoryType);
        if (strategy == null) {
            throw new IllegalStateException("No relation strategy for category: " + categoryType);
        }
        return strategy;
    }
}
