package io.kontur.insightsapi.configuration;

import graphql.Scalars;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.ExecutionStrategy;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphQLConfig {

    @Bean
    public GraphQLScalarType longType() {
        return Scalars.GraphQLLong;
    }

    @Bean
    public ExecutionStrategy executionStrategy(){
        return new AsyncExecutionStrategy();
    }
}
