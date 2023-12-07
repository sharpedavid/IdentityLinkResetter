import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The IdentityLinkResetter class is responsible for managing user accounts and their federated identity links in a Keycloak environment.
 * It provides functionality to delete all users in a specified realm and to remove orphaned federated identity links.
 * This class utilizes external configuration for setting up the connection to the Keycloak server and other operational parameters.
 */
public class IdentityLinkResetter {

    private final Keycloak keycloak;
    private final boolean realRun;
    private final String serverUrl;
    private final String clientRealm;
    private final String clientId;
    private final String clientSecret;
    private final String idpRealm;
    private final String applicationRealm;
    private final int userMax;
    private final List<String> deletedUsers = new ArrayList<>();
    private final List<String> deletedFederatedLinks = new ArrayList<>();
    static final Logger LOGGER = Logger.getLogger(IdentityLinkResetter.class.getName());

    static {
        LOGGER.setUseParentHandlers(false);
        LOGGER.setLevel(Level.INFO);
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.FINEST);
        LOGGER.addHandler(consoleHandler);
    }

    /**
     * Constructs an instance of IdentityLinkResetter.
     * Initializes the connection to the Keycloak server using configuration parameters loaded through ConfigLoader.
     * This includes setting up server URLs, client credentials, target realms, and operational parameters.
     *
     * @throws IOException If there is an error in loading configuration parameters or if any required parameter is missing.
     */
    public IdentityLinkResetter() throws IOException {
        ConfigLoader config = new ConfigLoader();
        serverUrl = config.getServerUrl();
        clientRealm = config.getClientRealm();
        clientId = config.getClientId();
        clientSecret = config.getClientSecret();
        idpRealm = config.getIdpRealm();
        applicationRealm = config.getApplicationRealm();
        realRun = config.isRealRun();
        userMax = config.getUserMax();

        keycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(clientRealm)
                .grantType("client_credentials")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
    }

    /**
     * Executes the identity link resetting process. This includes deleting all users in the specified IDP realm
     * and removing orphaned federated identity links in the application realm.
     */
    public void execute() {
        printInstructionsAndWait();
        deleteAllUsers(idpRealm);
        deleteOrphanedIdpLinks(applicationRealm, idpRealm);
        report();
    }

    /**
     * Prints the configuration instructions and waits for user confirmation to proceed.
     */
    private void printInstructionsAndWait() {
        var output = """
                This %s a dry run.
                Running against %s.
                Will delete all users in realm %s.
                Will delete all links to realm %s from realm %s.
                """;

        System.out.printf((output) + "%n", realRun ? "IS NOT" : "IS", serverUrl, idpRealm, idpRealm, applicationRealm);

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Please review the configuration. Press Enter to continue, or terminate the program to cancel.");
            scanner.nextLine();
        }
    }

    /**
     * Deletes all users in the specified realm.
     *
     * @param realm The realm from which users will be deleted.
     */
    private void deleteAllUsers(String realm) {
        LOGGER.log(Level.INFO, "Deleting all users in realm {0}.", realm);
        List<UserRepresentation> users = getUsers(realm);
        for (UserRepresentation user : users) {
            try {
                if (realRun) {
                    keycloak.realm(realm).users().delete(user.getId());
                }
                deletedUsers.add(user.getUsername());
                LOGGER.log(Level.FINEST, "Deleted user: {0}.", user.getUsername());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error deleting user: {0}.", user.getUsername());
                LOGGER.log(Level.WARNING, "Error deleting user stacktrace:", e);
            }
        }
    }

    /**
     * Deletes orphaned identity provider (IdP) links from users in a specified realm.
     *
     * @param realm The realm in which to delete IdP links.
     * @param identityProviderRealm The realm of the identity provider whose links are to be removed.
     */
    private void deleteOrphanedIdpLinks(String realm, String identityProviderRealm) {
        LOGGER.log(Level.INFO, "Deleting all {0} links in realm {1}.", new Object[]{identityProviderRealm, realm});
        List<UserRepresentation> users = getUsers(realm);
        for (UserRepresentation user : users) {
            List<FederatedIdentityRepresentation> federatedIdentities = keycloak.realm(realm).users().get(user.getId()).getFederatedIdentity();
            for (FederatedIdentityRepresentation fedIdentity : federatedIdentities) {
                if (fedIdentity.getIdentityProvider().equals(identityProviderRealm)) {
                    try {
                        if (realRun) {
                            keycloak.realm(realm).users().get(user.getId()).removeFederatedIdentity(fedIdentity.getIdentityProvider());
                        }
                        deletedFederatedLinks.add(user.getUsername() + " " + fedIdentity.getIdentityProvider());
                        LOGGER.log(Level.FINEST, "Removed IdP link for user: {0}.", user.getUsername());
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error removing IdP link for user: {0}.", user.getUsername());
                        LOGGER.log(Level.WARNING, "Error removing IdP link stacktrace:", e);
                    }
                }
            }
        }
    }

    /**
     * Retrieves a list of users from the specified realm, up to the configured maximum number.
     *
     * @param realm The realm from which users are retrieved.
     * @return A list of UserRepresentation objects.
     * @throws IllegalStateException If the number of users in the realm exceeds the configured maximum.
     */
    private List<UserRepresentation> getUsers(String realm) {
        UsersResource usersResource = keycloak.realm(realm).users();
        if (usersResource.count() > userMax) {
            throw new IllegalStateException(String.format("There are %s users in realm %s, but this application can process only %s.", usersResource.count(), realm, userMax));
        }

        List<UserRepresentation> users = usersResource.list(1, userMax);
        LOGGER.log(Level.FINER, "Found {0} users in realm {1}.", new Object[]{users.size(), realm});
        return users;
    }

    /**
     * Reports the results of the user and federated link deletion process.
     */
    private void report() {
        System.out.printf("Deleted %s users in realm %s:%n", deletedUsers.size(), idpRealm);
        for (String user : deletedUsers) {
            System.out.println(user);
        }

        System.out.printf("\nDeleted %s federated links from %s to %s:%n", deletedFederatedLinks.size(), applicationRealm, idpRealm);
        for (String link : deletedFederatedLinks) {
            System.out.println(link);
        }
    }

    /**
     * The main method to start the IdentityLinkResetter application.
     *
     * @param args Command line arguments (not used).
     * @throws IOException If there's an issue loading the configuration.
     */
    public static void main(String[] args) throws IOException {
        IdentityLinkResetter idirAadLinkReset = new IdentityLinkResetter();
        idirAadLinkReset.execute();
    }
}
