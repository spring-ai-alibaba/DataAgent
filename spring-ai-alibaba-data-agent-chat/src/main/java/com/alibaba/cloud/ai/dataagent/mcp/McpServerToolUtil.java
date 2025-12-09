package com.alibaba.cloud.ai.dataagent.mcp;

import org.springframework.context.support.GenericApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class McpServerToolUtil {

    public static <T> List<T> excludeMcpServerTool(
            GenericApplicationContext context,
            Class<T> type
    ) {
        String[] namesForType = context.getBeanNamesForType(type);
        Set<String> namesForAnnotation = Set.of(
                context.getBeanNamesForAnnotation(McpServerTool.class)
        );
        return Arrays.stream(namesForType)
                .filter(name -> !namesForAnnotation.contains(name))
                .map(name -> context.getBean(name, type))
                .toList();
    }

}
