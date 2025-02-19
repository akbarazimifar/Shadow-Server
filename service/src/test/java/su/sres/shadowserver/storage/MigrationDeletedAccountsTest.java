package su.sres.shadowserver.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

class MigrationDeletedAccountsTest {

  @RegisterExtension
  static DynamoDbExtension dynamoDbExtension = DynamoDbExtension.builder()
      .tableName("deleted_accounts_test")
      .hashKey(MigrationDeletedAccounts.KEY_UUID)
      .attributeDefinition(AttributeDefinition.builder()
          .attributeName(MigrationDeletedAccounts.KEY_UUID)          
          .attributeType(ScalarAttributeType.B)
          .build())
      .build();

  @Test
  void test() {

    final MigrationDeletedAccounts migrationDeletedAccounts = new MigrationDeletedAccounts(dynamoDbExtension.getDynamoDbClient(),
        dynamoDbExtension.getTableName());

    UUID firstUuid = UUID.randomUUID();
    UUID secondUuid = UUID.randomUUID();

    assertTrue(migrationDeletedAccounts.getRecentlyDeletedUuids().isEmpty());

    migrationDeletedAccounts.put(firstUuid);
    migrationDeletedAccounts.put(secondUuid);

    assertTrue(migrationDeletedAccounts.getRecentlyDeletedUuids().containsAll(List.of(firstUuid, secondUuid)));

    migrationDeletedAccounts.delete(List.of(firstUuid, secondUuid));

    assertTrue(migrationDeletedAccounts.getRecentlyDeletedUuids().isEmpty());
  }
}
