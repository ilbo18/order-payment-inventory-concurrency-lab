package com.ilbo18.concurrencylab.common.exception;

import com.ilbo18.concurrencylab.inventory.application.InventoryService;
import com.ilbo18.concurrencylab.inventory.presentation.InventoryController;
import com.ilbo18.concurrencylab.product.application.ProductService;
import com.ilbo18.concurrencylab.product.presentation.ProductController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ProductController.class,
        InventoryController.class
})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private InventoryService inventoryService;

    @Test
    void 상품_등록_요청_검증에_실패하면_필드_오류를_반환한다() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "price": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request."))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("price")));
    }

    @Test
    void 상품이_없으면_상품_없음_에러_응답을_반환한다() throws Exception {
        given(productService.get(999L)).willThrow(new CustomException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found. productId=999"));

        mockMvc.perform(get("/api/products/{productId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Product not found. productId=999"));
    }

    @Test
    void 이미_재고가_존재하면_재고_중복_에러_응답을_반환한다() throws Exception {
        given(inventoryService.register(1L, 10)).willThrow(new CustomException(ErrorCode.DUPLICATE_INVENTORY, "Inventory already exists. productId=1"));

        mockMvc.perform(post("/api/inventories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 1,
                                  "quantity": 10
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_INVENTORY"))
                .andExpect(jsonPath("$.message").value("Inventory already exists. productId=1"));
    }
}
