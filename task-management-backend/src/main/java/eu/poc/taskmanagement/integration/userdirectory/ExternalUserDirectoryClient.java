package eu.poc.taskmanagement.integration.userdirectory;

import java.util.Set;

public interface ExternalUserDirectoryClient {
    Set<String> fetchCreatedUsers(String taskId);
}
