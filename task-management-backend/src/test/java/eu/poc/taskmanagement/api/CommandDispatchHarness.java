package eu.poc.taskmanagement.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.axonframework.commandhandling.gateway.CommandGateway;

/**
 * Test helper that dispatches Axon commands inside a committed JTA transaction.
 */
@ApplicationScoped
class CommandDispatchHarness {

    @Inject
    CommandGateway commandGateway;

    @Transactional
    public void dispatch(Object command) {
        commandGateway.sendAndWait(command);
    }
}
