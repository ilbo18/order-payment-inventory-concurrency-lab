package com.ilbo18.concurrencylab.product.application;

import com.ilbo18.concurrencylab.common.exception.NotFoundException;
import com.ilbo18.concurrencylab.product.domain.Product;
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
@Import(ProductService.class)
class ProductServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProductService productService;

    @Autowired
    private TestEntityManager entityManager;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void 상품을_등록하고_단건_조회할_수_있다() {
        Product registeredProduct = productService.register("테스트 상품", BigDecimal.valueOf(10000));

        entityManager.flush();
        entityManager.clear();

        Product foundProduct = productService.get(registeredProduct.getId());

        assertThat(foundProduct.getId()).isEqualTo(registeredProduct.getId());
        assertThat(foundProduct.getName()).isEqualTo("테스트 상품");
        assertThat(foundProduct.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    void 존재하지_않는_상품을_조회하면_예외가_발생한다() {
        assertThatThrownBy(() -> productService.get(999L))
                .isInstanceOf(NotFoundException.class);
    }
}
