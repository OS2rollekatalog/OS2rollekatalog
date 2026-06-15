package dk.digitalidentity.rc.config;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.opensaml.core.config.InitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import dk.digitalidentity.kitos_client.KitosClientProperties;
import dk.digitalidentity.saml.config.SamlConfiguration;
import jakarta.annotation.PostConstruct;

// this class solves problems with CRaC
// 1 - it handles correct restore of a DataSource, throwing away any H2 DataSource created during checkpoint, and replaces it with the correct DataSource
// 2 - it reloads all system environment settings (including any for production environment), overwriting any set during checkpoint
// 3 - it performs flyway migrations
@Primary
@Component
@Profile("!test")
@Lazy(false) // if running with lazy initialization, we want to ensure this is initialized non-lazy
public class CracAwareness implements DataSource, Resource {
    private volatile HikariDataSource delegate;
    
    @Autowired
    private Environment springEnvironment;

    @Autowired
    private ConfigurableEnvironment environment;

    @Autowired
    private RoleCatalogueConfiguration configuration;
    
    @Autowired
    private SamlConfiguration samlConfiguration;
    
    @Autowired
    private KitosClientProperties kitosClientProperties;

    @PostConstruct
    public void init() throws InitializationException {
        this.delegate = createDataSource();

        Core.getGlobalContext().register(this);
    }
    
    private HikariDataSource createDataSource() throws InitializationException {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(getEnv("spring.datasource.url"));
        config.setUsername(getEnv("spring.datasource.username"));
        config.setPassword(getEnv("spring.datasource.password"));
        config.setMinimumIdle(getEnvInt("spring.datasource.hikari.minimum-idle"));
        config.setMaximumPoolSize(getEnvInt("spring.datasource.hikari.maximum-pool-size"));
        config.setIdleTimeout(getEnvInt("spring.datasource.hikari.idle-timeout"));
        config.setMaxLifetime(getEnvInt("spring.datasource.hikari.max-lifetime"));

        // hardcoded
    	config.setDriverClassName("org.mariadb.jdbc.Driver");

        return new HikariDataSource(config);
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {    	
    	// ensure SAML is initialized, so we do not have to do it after restore
        SamlConfiguration.init();

        delegate.close();
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        // reload system environment into Spring's property sources
        System.getenv().forEach((key, value) -> {
            System.setProperty(key, value);
        });
        
        // ensure configuration knows about all the loaded properties
        rebindConfiguration();

        // re-initialize Logback from its configuration file (so we can change logging settings in compose.yml)
        reloadLoggingConfiguration();

        // ensure we have a dataSource
        this.delegate = createDataSource();

        // create a flyway instance and perform a migrate
        flywayMigrate();
    }

	// delegate all DataSource methods

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegate.getConnection(username, password);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return delegate.isWrapperFor(iface);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
        throw new java.sql.SQLFeatureNotSupportedException();
    }

    // these two methods attempt to load from environment first (which is good for a docker environment section),
    // and if that fails, falls back to the Spring property settings (which in docker is the hardcoded values
    // that are read from the build property files, and are usually not what we want)
    private String getEnv(String key) throws InitializationException {
        String value = System.getenv(key);
        if (value == null) {
        	value = System.getProperty(key);
        }
        
        if (value == null) {
            value = springEnvironment.getProperty(key);
        }

        if (value != null) {
        	return value;
        }
        
        throw new InitializationException("Missing environment value for " + key);
    }

    private int getEnvInt(String key) throws InitializationException {
        String value = System.getenv(key);
        
        if (value == null) {
        	value = System.getProperty(key);
        }

        if (value == null) {
            value = springEnvironment.getProperty(key);
        }

        if (value == null) {
        	throw new InitializationException("Missing environment value for " + key);
        }
        
        return Integer.parseInt(value);
    }
    
    private void rebindConfiguration() {
        try {
            // add system properties
            org.springframework.core.env.MapPropertySource systemProps = 
                new org.springframework.core.env.MapPropertySource(
                    "crac-restored-system-properties",
                    System.getenv().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                            e -> e.getKey(),
                            e -> (Object) e.getValue()
                        ))
                );

            // load environment properties
            environment.getPropertySources().addFirst(systemProps);

            // re-bind the configuration properties bean

            org.springframework.boot.context.properties.bind.Binder
                .get(environment)
                .bind("rc", org.springframework.boot.context.properties.bind.Bindable.ofInstance(configuration));
            
            org.springframework.boot.context.properties.bind.Binder
	            .get(environment)
	            .bind("di.saml", org.springframework.boot.context.properties.bind.Bindable.ofInstance(samlConfiguration));
            
            org.springframework.boot.context.properties.bind.Binder
	            .get(environment)
	            .bind("di.kitos-client", org.springframework.boot.context.properties.bind.Bindable.ofInstance(kitosClientProperties));
        }
        catch (Exception ex) {
            System.out.println("CRaC ERROR: configuration rebind failed: " + ex.getMessage());
        }
    }
    
    private void flywayMigrate() {
        Flyway.configure()
	        .dataSource(this.delegate)
	        // must match FlywayConfiguration: scan db/migration recursively so both the mysql SQL
	        // migrations AND Java-based migrations (e.g. V1_346 in package db.migration) are resolved.
	        // Scanning only the mysql subfolder misses the Java migrations, so validateOnMigrate fails
	        // with "applied migration not resolved locally" on CRaC restore.
	        .locations("classpath:db/migration")
	        .table("schema_version")
	        .validateOnMigrate(true)
	        .baselineOnMigrate(true) // TODO: copied from FlywayConfiguration, not sure why we do this?
	        .outOfOrder(true)        // TODO: copied from FlywayConfiguration, not sure why we do this?
	        .load()
	        .migrate();
        
        // simple-queue migrations, copied from simple-queue code, as we need to rely on CRaC to do this in afterRestore(),
        // and not the framework doing it at startup
        Flyway.configure()
        	.dataSource(this.delegate)
            .configuration(Map.of("driver", "org.mariadb.jdbc.Driver"))
            .target(MigrationVersion.LATEST)
            .baselineOnMigrate(true)
            .table("simple_queue_migrations")
            .locations("classpath:/db/framework/simple_queue/migration/mariadb/")
            .load()
            .migrate();
    }

	private void reloadLoggingConfiguration() {
		try {
	        LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

	        LoggingInitializationContext initializationContext = new LoggingInitializationContext(environment);

	        loggingSystem.initialize(initializationContext, null, null);
	        
            // apply logging.level.* from the (refreshed) Spring environment.
            org.springframework.boot.context.properties.bind.Binder binder = org.springframework.boot.context.properties.bind.Binder.get(environment);

            binder.bind("logging.level", org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class, String.class))
                  .ifBound(levels -> levels.forEach((logger, level) -> {
                      String name = "ROOT".equalsIgnoreCase(logger) ? null : logger;
                      org.springframework.boot.logging.LogLevel parsed = org.springframework.boot.logging.LogLevel.valueOf(level.toUpperCase());
                      loggingSystem.setLogLevel(name, parsed);
                  }));

	    }
		catch (Exception e) {
	        System.out.println("CRaC: Logback re-init failed: " + e.getMessage());
	    }
	}
}
