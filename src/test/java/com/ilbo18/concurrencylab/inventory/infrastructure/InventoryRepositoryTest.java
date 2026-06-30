package com.ilbo18.concurrencylab.inventory.infrastructure;

import com.ilbo18.concurrencylab.inventory.domain.Inventory;
import com.ilbo18.concurrencylab.product.domain.Product;
import com.ilbo18.concurrencylab.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void 상품을_저장한_뒤_상품_id로_재고를_조회할_수_있다() {
        Product product = productRepository.save(new Product("테스트 상품", BigDecimal.valueOf(10000)));
        Inventory inventory = inventoryRepository.save(new Inventory(product.getId(), 10));

        entityManager.flush();
        entityManager.clear();

        // 재고는 Product 객체 관계가 아니라 productId 값 참조로 상품과 연결된다.
        Optional<Inventory> foundInventory = inventoryRepository.findByProductId(product.getId());

        assertThat(foundInventory).isPresent();
        assertThat(foundInventory.get().getId()).isEqualTo(inventory.getId());
        assertThat(foundInventory.get().getProductId()).isEqualTo(product.getId());
        assertThat(foundInventory.get().getQuantity()).isEqualTo(10);
    }

    @Test
    void 하나의_상품에는_재고를_하나만_저장할_수_있다() {
        Product product = productRepository.save(new Product("중복 재고 상품", BigDecimal.valueOf(15000)));
        inventoryRepository.saveAndFlush(new Inventory(product.getId(), 10));

        // 상품별 재고가 여러 행으로 분리되면 차감 기준이 흔들리므로 DB unique 제약으로 차단한다.
        assertThatThrownBy(() -> inventoryRepository.saveAndFlush(new Inventory(product.getId(), 20)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
