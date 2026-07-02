package com.ilbo18.concurrencylab.inventory.application;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.inventory.domain.Inventory;
import com.ilbo18.concurrencylab.product.domain.Product;
import com.ilbo18.concurrencylab.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(InventoryService.class)
class InventoryServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void 상품_id로_재고를_등록하고_조회할_수_있다() {
        Product product = saveProduct("재고 등록 상품");
        Inventory registeredInventory = inventoryService.register(product.getId(), 10);

        entityManager.flush();
        entityManager.clear();

        Inventory foundInventory = inventoryService.getByProductId(product.getId());

        assertThat(foundInventory.getId()).isEqualTo(registeredInventory.getId());
        assertThat(foundInventory.getProductId()).isEqualTo(product.getId());
        assertThat(foundInventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void 같은_상품에_재고를_중복_등록하면_예외가_발생한다() {
        Product product = saveProduct("중복 재고 상품");
        inventoryService.register(product.getId(), 10);

        assertThatThrownBy(() -> inventoryService.register(product.getId(), 20))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_INVENTORY));
    }

    @Test
    void 존재하지_않는_상품에_재고를_등록하면_예외가_발생한다() {
        assertThatThrownBy(() -> inventoryService.register(999L, 10))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    void 재고_수량이_음수이면_예외가_발생한다() {
        Product product = saveProduct("음수 재고 상품");

        assertThatThrownBy(() -> inventoryService.register(product.getId(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 상품은_있지만_재고가_없으면_조회_예외가_발생한다() {
        Product product = saveProduct("재고 미등록 상품");

        assertThatThrownBy(() -> inventoryService.getByProductId(product.getId()))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND));
    }

    private Product saveProduct(String name) {
        return productRepository.save(new Product(name, BigDecimal.valueOf(10000)));
    }
}
