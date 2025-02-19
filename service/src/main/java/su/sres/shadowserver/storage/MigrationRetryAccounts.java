package su.sres.shadowserver.storage;

import com.google.common.annotations.VisibleForTesting;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import su.sres.shadowserver.util.AttributeValues;
import su.sres.shadowserver.util.UUIDUtil;

public class MigrationRetryAccounts extends AbstractScyllaDbStore {

  private final String tableName;

  static final String KEY_UUID = "U";

  public MigrationRetryAccounts(DynamoDbClient dynamoDb, String tableName) {
    super(dynamoDb);

    this.tableName = tableName;
  }

  public void put(UUID uuid) {
    db().putItem(PutItemRequest.builder()
        .tableName(tableName)
        .item(primaryKey(uuid))
        .build());
  }

  public List<UUID> getUuids(int max) {

    final List<UUID> uuids = new ArrayList<>();

    for (ScanResponse response : db().scanPaginator(ScanRequest.builder().tableName(tableName).build())) {

      for (Map<String, AttributeValue> item : response.items()) {
        uuids.add(AttributeValues.getUUID(item, KEY_UUID, null));

        if (uuids.size() >= max) {
          break;
        }
      }

      if (uuids.size() >= max) {
        break;
      }
    }

    return uuids;
  }

  @VisibleForTesting
  public static Map<String, AttributeValue> primaryKey(UUID uuid) {
    return Map.of(KEY_UUID, AttributeValues.fromUUID(uuid));
  }

}
