package com.commons.exception.autoconfig;

import com.commons.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Registers {@link GlobalExceptionHandler} for every Spring Boot service that depends on commons-dto.
 * Component-scan of {@code com.commons} alone is not always reliable across packaged JARs.
 */
@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class CommonsExceptionAutoConfiguration {
}
