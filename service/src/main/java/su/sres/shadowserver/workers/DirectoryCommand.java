/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.workers;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.fasterxml.jackson.databind.DeserializationFeature;
import net.sourceforge.argparse4j.inf.Namespace;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static com.codahale.metrics.MetricRegistry.name;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jdbi.v3.core.Jdbi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.Application;
import io.dropwizard.cli.EnvironmentCommand;
import io.dropwizard.jdbi3.JdbiFactory;
import io.dropwizard.setup.Environment;
import io.lettuce.core.resource.ClientResources;
import io.micrometer.core.instrument.Metrics;
import su.sres.shadowserver.WhisperServerConfiguration;
import su.sres.shadowserver.configuration.AccountsScyllaDbConfiguration;
import su.sres.shadowserver.configuration.MessageScyllaDbConfiguration;
import su.sres.shadowserver.configuration.ScyllaDbConfiguration;
import su.sres.shadowserver.metrics.PushLatencyManager;
import su.sres.shadowserver.providers.RedisClientFactory;
import su.sres.shadowserver.redis.FaultTolerantRedisCluster;
import su.sres.shadowserver.redis.ReplicatedJedisPool;
import su.sres.shadowserver.storage.Accounts;
import su.sres.shadowserver.storage.AccountsManager;
import su.sres.shadowserver.storage.AccountsScyllaDb;
import su.sres.shadowserver.storage.DirectoryManager;
import su.sres.shadowserver.storage.FaultTolerantDatabase;
import su.sres.shadowserver.storage.KeysScyllaDb;
import su.sres.shadowserver.storage.MessagesCache;
import su.sres.shadowserver.storage.MessagesManager;
import su.sres.shadowserver.storage.MessagesScyllaDb;
import su.sres.shadowserver.storage.MigrationDeletedAccounts;
import su.sres.shadowserver.storage.MigrationRetryAccounts;
import su.sres.shadowserver.storage.Profiles;
import su.sres.shadowserver.storage.ProfilesManager;
import su.sres.shadowserver.storage.ReportMessageManager;
import su.sres.shadowserver.storage.ReportMessageScyllaDb;
import su.sres.shadowserver.storage.ReservedUsernames;
import su.sres.shadowserver.storage.Usernames;
import su.sres.shadowserver.storage.UsernamesManager;
import su.sres.shadowserver.util.ScyllaDbFromConfig;

// TODO: Migrate to Scylla
public class DirectoryCommand extends EnvironmentCommand<WhisperServerConfiguration> {

  private final Logger logger = LoggerFactory.getLogger(DirectoryCommand.class);

  public DirectoryCommand() {
    super(new Application<WhisperServerConfiguration>() {
      @Override
      public void run(WhisperServerConfiguration configuration, Environment environment) throws Exception {

      }
    }, "directory", "Update directory from PostgreSQL. WARNING: This will flush all your incremental updates!");
  }

  @Override
  protected void run(Environment environment, Namespace namespace, WhisperServerConfiguration configuration)
      throws Exception {
    try {
      environment.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

      JdbiFactory jdbiFactory = new JdbiFactory();
      Jdbi accountJdbi = jdbiFactory.build(environment, configuration.getAccountsDatabaseConfiguration(), "accountdb");
      FaultTolerantDatabase accountDatabase = new FaultTolerantDatabase("account_database_directory", accountJdbi, configuration.getAccountsDatabaseConfiguration().getCircuitBreakerConfiguration());

      ClientResources redisClusterClientResources = ClientResources.builder().build();

      MessageScyllaDbConfiguration scyllaMessageConfig = configuration.getMessageScyllaDbConfiguration();
      ScyllaDbConfiguration scyllaKeysConfig = configuration.getKeysScyllaDbConfiguration();
      AccountsScyllaDbConfiguration scyllaAccountsConfig = configuration.getAccountsScyllaDbConfiguration();
      
      ScyllaDbConfiguration scyllaMigrationDeletedAccountsConfig = configuration.getMigrationDeletedAccountsScyllaDbConfiguration();
      ScyllaDbConfiguration scyllaMigrationRetryAccountsConfig = configuration.getMigrationRetryAccountsScyllaDbConfiguration(); 
      
      ScyllaDbConfiguration scyllaReportMessageConfig = configuration.getReportMessageScyllaDbConfiguration(); 
    
      ThreadPoolExecutor accountsScyllaDbMigrationThreadPool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
          new LinkedBlockingDeque<>());

      DynamoDbClient reportMessagesScyllaDb = ScyllaDbFromConfig.client(scyllaReportMessageConfig);
      DynamoDbClient messageScyllaDb = ScyllaDbFromConfig.client(scyllaMessageConfig);
      DynamoDbClient preKeysScyllaDb = ScyllaDbFromConfig.client(scyllaKeysConfig);
      DynamoDbClient accountsScyllaDbClient = ScyllaDbFromConfig.client(scyllaAccountsConfig);
      DynamoDbAsyncClient accountsScyllaDbAsyncClient = ScyllaDbFromConfig.asyncClient(scyllaAccountsConfig, accountsScyllaDbMigrationThreadPool);
      
      DynamoDbClient migrationDeletedAccountsScyllaDb = ScyllaDbFromConfig.client(scyllaMigrationDeletedAccountsConfig);
      DynamoDbClient migrationRetryAccountsScyllaDb = ScyllaDbFromConfig.client(scyllaMigrationRetryAccountsConfig);
      
      MigrationDeletedAccounts migrationDeletedAccounts = new MigrationDeletedAccounts(migrationDeletedAccountsScyllaDb, scyllaMigrationDeletedAccountsConfig.getTableName());
      MigrationRetryAccounts migrationRetryAccounts = new MigrationRetryAccounts(migrationRetryAccountsScyllaDb, scyllaMigrationRetryAccountsConfig.getTableName());      
  
      FaultTolerantRedisCluster cacheCluster = new FaultTolerantRedisCluster("main_cache_cluster", configuration.getCacheClusterConfiguration(), redisClusterClientResources);
      FaultTolerantRedisCluster messageInsertCacheCluster = new FaultTolerantRedisCluster("message_insert_cluster", configuration.getMessageCacheConfiguration().getRedisClusterConfiguration(), redisClusterClientResources);
      FaultTolerantRedisCluster messageReadDeleteCluster = new FaultTolerantRedisCluster("message_read_delete_cluster", configuration.getMessageCacheConfiguration().getRedisClusterConfiguration(), redisClusterClientResources);
      FaultTolerantRedisCluster metricsCluster = new FaultTolerantRedisCluster("metrics_cluster", configuration.getMetricsClusterConfiguration(), redisClusterClientResources);

      ExecutorService keyspaceNotificationDispatchExecutor = environment.lifecycle().executorService(name(getClass(), "keyspaceNotification-%d")).maxThreads(4).build();
      
      
          
      Accounts accounts = new Accounts(accountDatabase);
      AccountsScyllaDb accountsScyllaDb = new AccountsScyllaDb(accountsScyllaDbClient, accountsScyllaDbAsyncClient, accountsScyllaDbMigrationThreadPool, scyllaAccountsConfig.getTableName(), scyllaAccountsConfig.getUserLoginTableName(), scyllaAccountsConfig.getMiscTableName(), migrationDeletedAccounts, migrationRetryAccounts);
      Usernames usernames = new Usernames(accountDatabase);
      Profiles profiles = new Profiles(accountDatabase);
      ReservedUsernames reservedUsernames = new ReservedUsernames(accountDatabase);
      KeysScyllaDb keysScyllaDb = new KeysScyllaDb(preKeysScyllaDb, configuration.getKeysScyllaDbConfiguration().getTableName());
      MessagesScyllaDb messagesScyllaDb = new MessagesScyllaDb(messageScyllaDb, scyllaMessageConfig.getTableName(), scyllaMessageConfig.getTimeToLive());

      ReplicatedJedisPool redisClient = new RedisClientFactory("directory_cache_directory_command",
          configuration.getDirectoryConfiguration().getUrl(),
          configuration.getDirectoryConfiguration().getReplicaUrls(),
          configuration.getDirectoryConfiguration().getCircuitBreakerConfiguration()).getRedisClientPool();

      DirectoryManager directory = new DirectoryManager(redisClient);

      MessagesCache messagesCache = new MessagesCache(messageInsertCacheCluster, messageReadDeleteCluster, keyspaceNotificationDispatchExecutor);
      PushLatencyManager pushLatencyManager = new PushLatencyManager(metricsCluster);

      UsernamesManager usernamesManager = new UsernamesManager(usernames, reservedUsernames, cacheCluster);
      ProfilesManager profilesManager = new ProfilesManager(profiles, cacheCluster);
      ReportMessageScyllaDb reportMessageScyllaDb = new ReportMessageScyllaDb(reportMessagesScyllaDb, scyllaReportMessageConfig.getTableName());
      
      ReportMessageManager reportMessageManager = new ReportMessageManager(reportMessageScyllaDb, Metrics.globalRegistry);
      MessagesManager messagesManager = new MessagesManager(messagesScyllaDb, messagesCache, pushLatencyManager, reportMessageManager);

      AccountsManager accountsManager = new AccountsManager(accounts, accountsScyllaDb, directory, cacheCluster, keysScyllaDb, messagesManager, usernamesManager, profilesManager);

      PlainDirectoryUpdater updater = new PlainDirectoryUpdater(accountsManager);

      if (accountsManager.getAccountCreationLock() || directory.getDirectoryReadLock()
          || accountsManager.getDirectoryRestoreLock()) {

        logger.warn("There's a pending operation on directory right now, please try again a bit later");
        return;
      }

      updater.updateFromLocalDatabase();

    } catch (Exception ex) {
      logger.warn("Directory Exception", ex);
      throw new RuntimeException(ex);
    }
  }
}
