package eu.poc.taskmanagement.test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;

/**
 * Test helper that dispatches Axon commands inside a committed JTA transaction.
 */
@ApplicationScoped
public class CommandDispatchHarness {

    @Inject
    CommandGateway commandGateway;

    @Transactional
    public void dispatch(Object command) {
        commandGateway.sendAndWait(command);
    }
}
