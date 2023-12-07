# Identity Link Resetter for Keycloak

The Identity Link Resetter is a Java application designed to manage user accounts and their federated identity links in a Keycloak environment. It provides functionality to delete all users in a specified realm and to remove orphaned federated identity links.

## Features

- Delete all users in a specified realm within Keycloak.
- Remove orphaned federated identity links in a specified realm.
- Dry run capability to simulate actions without making actual changes.
- Externalized configuration for easy customization and deployment.

## Prerequisites

- Java JDK 11 or later.
- Access to a Keycloak server.
- A service account with relevant permissions to manage users and federated identity links in Keycloak. This should be created in the `master` realm with permissions on all targeted realms. 

## Configuration

The application uses a properties file (`config.properties`) for configuration.

## Installation

**Note:** The client secret is expected to be set as an environment variable for security purposes.

1. Clone the repository.
2. Navigate to the project directory:
3. Ensure `src/main/resources/config.properties` is configured as per your Keycloak environment:
4. Set the client secret environment variable. The name of the variable is configurable in `config.properties`.
5. Build the project using `mvn package`.

## Usage

Run the application inside your IDE or using the following command:

```shell
java -cp "target/IdirAadUserLinkReset-1.0-SNAPSHOT.jar;target/dependency/*" IdentityLinkResetter
```

Follow the on-screen instructions to proceed with the user and federated link deletion process.

## Program Purpose and Functionality

### Overview

This program is designed to address a specific issue encountered when the configuration of an external Identity Provider (IDP) changes in a way that affects user access in our systems. Specifically, it deals with the scenario where the `sub` values (subject identifiers) in the external IDP have changed. This change disrupts the established links between users in our Keycloak IDP realm and the external IDP, leading to "Account already exists" errors during user authentication.

### Why Is This Program Necessary?

Our authentication setup involves two realms in Keycloak:

* IDP Realm: This realm is linked to an external IDP.
* Application Realm: This realm contains our applications and services.

Due to changes in the external IDP configuration, all `sub` values for our users have been altered. In Keycloak, these sub values are crucial as they are used to link accounts between our IDP Realm and the Application Realm. Since the sub values have changed but the usernames remain the same, we encounter authentication issues where existing links are now invalid, and the system cannot automatically link the new sub values to existing user accounts.
Solution

The program automates the process of restoring access by performing two main functions:

1. Deleting Users in the IDP Realm: It systematically deletes all users in the IDP Realm. This action is necessary because the existing user accounts are linked to outdated sub values and are therefore obsolete.
2. Removing Orphaned Links in the Application Realm: After removing users from the IDP Realm, the program then cleans up the Application Realm by removing any orphaned federated identity links. These links are remnants of the previous connections to the now-deleted IDP Realm accounts.

### Outcome

By executing these steps, we essentially reset the linkage state. Since our system is set up to auto-link users by username, new login attempts from the external IDP will automatically create fresh, valid links based on the current sub values and the unchanged usernames. This process restores user access without the need for manual intervention in link recreation.

### Usage Precautions

1. Data Sensitivity: This program performs irreversible operations by deleting users and links. Ensure you have appropriate backups and have validated the program's behavior in a test environment before running it in production.
2. Configurations Check: Before executing the program, review the configurations carefully, especially the realm names and connection settings.