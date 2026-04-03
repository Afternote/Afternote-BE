package com.example.afternote.domain.afternote.service.relation;

import com.example.afternote.domain.afternote.model.AfternoteCategoryType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AfternoteRelationStrategyFactory {

    private final List<AfternoteCategoryRelationStrategy> strategies;
    private final Map<AfternoteCategoryType, AfternoteCategoryRelationStrategy> strategyMap =
            new EnumMap<>(AfternoteCategoryType.class);

    @PostConstruct
    void init() {
        for (AfternoteCategoryRelationStrategy strategy : strategies) {
            strategyMap.put(strategy.category(), strategy);
        }
    }

    public AfternoteCategoryRelationStrategy get(AfternoteCategoryType categoryType) {
        AfternoteCategoryRelationStrategy strategy = strategyMap.get(categoryType);
        if (strategy == null) {
            throw new IllegalStateException("No relation strategy for category: " + categoryType);
        }
        return strategy;
    }
}
