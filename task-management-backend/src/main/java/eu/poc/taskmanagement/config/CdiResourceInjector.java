package eu.poc.taskmanagement.config;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.modelling.saga.ResourceInjector;

import java.lang.reflect.Field;

/**
 * Axon {@link ResourceInjector} backed by Quarkus CDI.
 *
 * <h2>Why this is needed</h2>
 * Axon's default {@code SpringResourceInjector} relies on Spring's
 * {@code ApplicationContext}.  In a Quarkus / CDI environment there is no
 * Spring context, so we provide our own injector that resolves {@code @Inject}
 * annotated fields on Saga classes from the CDI {@code BeanManager}.
 *
 * <h2>What it injects</h2>
 * Any field annotated with {@code jakarta.inject.Inject} in a Saga class is
 * resolved via the CDI container and set via reflection.  The fields are
 * typically marked {@code transient} on the Saga so they are not persisted in
 * the {@code JpaSagaStore} — they are re-injected every time a Saga instance
 * is loaded from storage.
 *
 * <h2>Supported types</h2>
 * <ul>
 *   <li>{@code DeadlineManager} — used by {@code TaskDeadlineSaga} to schedule/cancel jobs</li>
 *   <li>{@code EventBus} — used to publish {@code TaskDeadlineExceededEvent}</li>
 * </ul>
 *
 * @see AxonConfig#configurer() where this injector is registered
 */
@Slf4j
@RequiredArgsConstructor
public class CdiResourceInjector implements ResourceInjector {

    private final BeanManager beanManager;

    /**
     * Injects all {@code @Inject}-annotated fields on the given saga instance.
     * Fields are set via reflection, including private / package-private ones.
     */
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
        field.setAccessible(true);
        try {
            Object bean = lookupBean(field.getType());
            if (bean != null) {
                field.set(saga, bean);
                log.debug("CDI injected {} into {}", field.getType().getSimpleName(),
                        saga.getClass().getSimpleName());
            } else {
                log.warn("No CDI bean found for type {} — field {} on {} will be null",
                        field.getType().getSimpleName(), field.getName(),
                        saga.getClass().getSimpleName());
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "CdiResourceInjector: failed to set field %s on %s"
                            .formatted(field.getName(), saga.getClass().getSimpleName()), e);
        }
    }

    /**
     * Looks up a CDI bean by type.  Returns {@code null} if no bean is found
     * rather than throwing, so individual missing beans produce a warning
     * instead of crashing the whole Saga load.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object lookupBean(Class<?> beanType) {
        var beans = beanManager.getBeans(beanType);
        if (beans.isEmpty()) {
            return null;
        }
        var bean = beanManager.resolve((java.util.Set) beans);
        var ctx = beanManager.createCreationalContext(bean);
        return beanManager.getReference(bean, beanType, ctx);
    }
}
