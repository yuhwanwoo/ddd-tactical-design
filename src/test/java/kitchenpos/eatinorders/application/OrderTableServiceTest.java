package kitchenpos.eatinorders.application;

import kitchenpos.deliveryorders.application.InMemoryMenuClient;
import kitchenpos.deliveryorders.domain.MenuClient;
import kitchenpos.eatinorders.domain.OrderRepository;
import kitchenpos.eatinorders.domain.OrderStatus;
import kitchenpos.eatinorders.shared.dto.request.EatInOrderTableChangeNumberOfGuestsRequest;
import kitchenpos.eatinorders.shared.dto.request.OrderTableCreateRequest;
import kitchenpos.eatinorders.tobe.domain.order.EatInOrder;
import kitchenpos.eatinorders.tobe.domain.order.EatInOrderLineItem;
import kitchenpos.eatinorders.tobe.domain.order.EatInOrderLineItems;
import kitchenpos.eatinorders.tobe.domain.order.EatInOrderRepository;
import kitchenpos.eatinorders.tobe.domain.ordertable.NumberOfGuests;
import kitchenpos.eatinorders.tobe.domain.ordertable.OrderTable;
import kitchenpos.eatinorders.tobe.domain.ordertable.OrderTableName;
import kitchenpos.eatinorders.tobe.domain.ordertable.OrderTableRepository;
import kitchenpos.menus.application.InMemoryMenuRepository;
import kitchenpos.menus.tobe.domain.menu.Menu;
import kitchenpos.menus.tobe.domain.menu.MenuRepository;
import kitchenpos.products.application.InMemoryProductRepository;
import kitchenpos.products.tobe.domain.Product;
import kitchenpos.products.tobe.domain.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static kitchenpos.Fixtures.displayedMenu;
import static kitchenpos.Fixtures.eatInOrder;
import static kitchenpos.Fixtures.menuProduct;
import static kitchenpos.Fixtures.order;
import static kitchenpos.Fixtures.orderLineItem;
import static kitchenpos.Fixtures.orderTable;
import static kitchenpos.Fixtures.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class OrderTableServiceTest {
    private OrderTableRepository orderTableRepository;

    private EatInOrderRepository eatInOrderRepository;
    private OrderTableService orderTableService;
    private MenuRepository menuRepository;
    private MenuClient menuClient;
    private ProductRepository productRepository;
    Product product;
    Menu displayedMenu;

    @BeforeEach
    void setUp() {
        orderTableRepository = new InMemoryOrderTableRepository();
        eatInOrderRepository = new InMemoryEatInOrderRepository();

        productRepository = new InMemoryProductRepository();
        orderTableService = new OrderTableService(orderTableRepository, eatInOrderRepository);
        product = productRepository.save(product());
        menuRepository = new InMemoryMenuRepository();
        menuClient = new InMemoryMenuClient(menuRepository);
        displayedMenu = menuRepository.save(displayedMenu(1L, menuProduct(product, 1L)));
    }

    @DisplayName("주문 테이블을 등록할 수 있다.")
    @Test
    void create() {
        final OrderTableCreateRequest expected = createOrderTableRequest("1번");
        final OrderTable actual = orderTableService.create(expected);
        assertThat(actual).isNotNull();
        assertAll(
            () -> assertThat(actual.getId()).isNotNull(),
            () -> assertThat(actual.getName()).isEqualTo(expected.getName()),
            () -> assertThat(actual.getNumberOfGuests()).isZero(),
            () -> assertThat(actual.isOccupied()).isFalse()
        );
    }

    @DisplayName("주문 테이블의 이름이 올바르지 않으면 등록할 수 없다.")
    @NullAndEmptySource
    @ParameterizedTest
    void create(final String name) {
        final OrderTableCreateRequest expected = createOrderTableRequest(name);
        assertThatThrownBy(() -> orderTableService.create(expected))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("빈 테이블을 해지할 수 있다.")
    @Test
    void sit() {
        final UUID orderTableId = orderTableRepository.save(orderTable(false, 0)).getId();
        final OrderTable actual = orderTableService.sit(orderTableId);
        assertThat(actual.isOccupied()).isTrue();
    }

    @DisplayName("빈 테이블로 설정할 수 있다.")
    @Test
    void clear() {
        final UUID orderTableId = orderTableRepository.save(orderTable(true, 4)).getId();
        final OrderTable actual = orderTableService.clear(orderTableId);
        assertAll(
            () -> assertThat(actual.getNumberOfGuests()).isZero(),
            () -> assertThat(actual.isOccupied()).isFalse()
        );
    }

    @DisplayName("완료되지 않은 주문이 있는 주문 테이블은 빈 테이블로 설정할 수 없다.")
    @Test
    void clearWithUncompletedOrders() {
        final OrderTable orderTable = orderTableRepository.save(orderTable(true, 4));
        final UUID orderTableId = orderTable.getId();
        eatInOrderRepository.save(eatInOrder(OrderStatus.ACCEPTED, orderTable, displayedMenu));
        assertThatThrownBy(() -> orderTableService.clear(orderTableId))
            .isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("방문한 손님 수를 변경할 수 있다.")
    @Test
    void changeNumberOfGuests() {
        final UUID orderTableId = orderTableRepository.save(orderTable(true, 0)).getId();
        final EatInOrderTableChangeNumberOfGuestsRequest expected = changeNumberOfGuestsRequest(4);
        final OrderTable actual = orderTableService.changeNumberOfGuests(orderTableId, expected);
        assertThat(actual.getNumberOfGuests()).isEqualTo(4);
    }

    @DisplayName("방문한 손님 수가 올바르지 않으면 변경할 수 없다.")
    @ValueSource(ints = -1)
    @ParameterizedTest
    void changeNumberOfGuests(final int numberOfGuests) {
        final UUID orderTableId = orderTableRepository.save(orderTable(true, 0)).getId();
        final EatInOrderTableChangeNumberOfGuestsRequest expected = changeNumberOfGuestsRequest(numberOfGuests);
        assertThatThrownBy(() -> orderTableService.changeNumberOfGuests(orderTableId, expected))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("빈 테이블은 방문한 손님 수를 변경할 수 없다.")
    @Test
    void changeNumberOfGuestsInEmptyTable() {
        final UUID orderTableId = orderTableRepository.save(orderTable(false, 0)).getId();
        final EatInOrderTableChangeNumberOfGuestsRequest expected = changeNumberOfGuestsRequest(4);
        assertThatThrownBy(() -> orderTableService.changeNumberOfGuests(orderTableId, expected))
            .isInstanceOf(IllegalStateException.class);
    }

    @DisplayName("주문 테이블의 목록을 조회할 수 있다.")
    @Test
    void findAll() {
        orderTableRepository.save(orderTable());
        final List<OrderTable> actual = orderTableService.findAll();
        assertThat(actual).hasSize(1);
    }

    private OrderTableCreateRequest createOrderTableRequest(final String name) {
        return new OrderTableCreateRequest(name);
    }

    private EatInOrderTableChangeNumberOfGuestsRequest changeNumberOfGuestsRequest(final int numberOfGuests) {
        return new EatInOrderTableChangeNumberOfGuestsRequest(numberOfGuests);
    }
}
