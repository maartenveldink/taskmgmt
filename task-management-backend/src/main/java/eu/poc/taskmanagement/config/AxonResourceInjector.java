package eu.poc.taskmanagement.config;

import eu.poc.taskmanagement.integration.userdirectory.ExternalUserDirectoryClient;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.Configuration;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.modelling.saga.ResourceInjector;

import java.lang.reflect.Field;

/**
 * Axon {@link ResourceInjector} that resolves {@code @Inject}-annotated saga
 * fields directly from the Axon {@link Configuration}.
 *
 * <h2>Why not CDI?</h2>
 * The previous {@code CdiResourceInjector} used Quarkus's {@code BeanManager}
 * to look up Axon interface types ({@code DeadlineManager}, {@code EventBus}) at
 * runtime.  Quarkus's build-time CDI processor does not always register these
 * external interface types as resolvable CDI beans, causing {@code beanManager
 * .getBeans(DeadlineManager.class)} to return an empty set and leaving the saga
 * fields {@code null}.
 *
 * <h2>How it works</h2>
 * The injector holds a reference to the fully-built Axon {@link Configuration}.
 * When a Saga instance is created or loaded, {@link #injectResources(Object)}
 * iterates over all {@code @jakarta.inject.Inject}-annotated fields and resolves
 * the value from the configuration:
 * <ul>
 *   <li>{@link DeadlineManager} → {@code configuration.getComponent(DeadlineManager.class)}</li>
 *   <li>{@link EventBus}        → {@code configuration.eventBus()}</li>
 * </ul>
 * Both fields are {@code transient} on the Saga, so they survive serialization
 * correctly (excluded from JPA saga state) and are re-injected on each load.
 */
@Slf4j
@RequiredArgsConstructor
public class AxonResourceInjector implements ResourceInjector {

    private final Configuration configuration;
    private final ExternalUserDirectoryClient externalUserDirectoryClient;

    @Override
    public void injectResources(Object saga) {
        Class<?> type = saga.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    injectField(saga, field);
                }
            }
            type = type.getSuperclass();
        }
    }

    private void injectField(Object saga, Field field) {
        Object value = resolveBean(field.getType());
        if (value == null) {
            log.warn("AxonResourceInjector: no value found for type {} — field {} on {} will be null",
                    field.getType().getSimpleName(), field.getName(), saga.getClass().getSimpleName());
            return;
        }
        field.setAccessible(true);
        try {
            field.set(saga, value);
            log.debug("AxonResourceInjector: injected {} into {}",
                    field.getType().getSimpleName(), saga.getClass().getSimpleName());
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "AxonResourceInjector: failed to set field %s on %s"
                            .formatted(field.getName(), saga.getClass().getSimpleName()), e);
        }
    }

    /**
     * Resolves the Axon component for the given type.
     * Extend this method if additional injectable types are needed in future sagas.
     */
    private Object resolveBean(Class<?> fieldType) {
        if (DeadlineManager.class.isAssignableFrom(fieldType)) {
            return configuration.getComponent(DeadlineManager.class);
        }
        if (EventBus.class.isAssignableFrom(fieldType)) {
            return configuration.eventBus();
        }
        if (CommandGateway.class.isAssignableFrom(fieldType)) {
            return configuration.commandGateway();
        }
        if (ExternalUserDirectoryClient.class.isAssignableFrom(fieldType)) {
            return externalUserDirectoryClient;
        }
        log.warn("AxonResourceInjector: unknown injectable type {} — returning null", fieldType.getSimpleName());
        return null;
    }
}
