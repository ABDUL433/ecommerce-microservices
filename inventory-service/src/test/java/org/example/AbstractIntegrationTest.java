package org.example;

import org.example.dto.CreateProductRequest;
import org.example.entity.Inventory;
import org.example.repository.InventoryRepository;
import org.example.repository.StockReservationRepository;
import org.example.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Starts a throw-away Postgres and Kafka in Docker (once for all test classes) and points the
 * application at them, so tests never touch your real dev databases or topics.
 * Needs Docker Desktop to be running.
 */
@SpringBootTest(properties = "spring.kafka.consumer.auto-offset-reset=earliest")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    static {
        // The JVM zone on some PCs is "Asia/Calcutta", which newer Postgres images reject.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    protected ProductService productService;

    @Autowired
    protected InventoryRepository inventoryRepository;

    @Autowired
    protected StockReservationRepository reservationRepository;

    /** Creates a product with the given stock and returns its id. */
    protected UUID newProduct(int stock) {
        return productService.createProduct(new CreateProductRequest(
                "SKU-" + UUID.randomUUID(), "Test product", null, new BigDecimal("10.00"), stock)).id();
    }

    protected Inventory inventoryOf(UUID productId) {
        return inventoryRepository.findByProductId(productId).orElseThrow();
    }

    /** Polls until the condition is true (events travel through Kafka, so results are not instant). */
    protected static void await(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for: " + what);
            }
        }
        throw new AssertionError("Timed out waiting for: " + what);
    }
}

