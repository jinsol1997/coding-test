package com.seowon.coding.service;

import com.seowon.coding.domain.model.Order;
import com.seowon.coding.domain.model.OrderItem;
import com.seowon.coding.domain.model.ProcessingStatus;
import com.seowon.coding.domain.model.Product;
import com.seowon.coding.domain.repository.OrderRepository;
import com.seowon.coding.domain.repository.ProcessingStatusRepository;
import com.seowon.coding.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProcessingStatusRepository processingStatusRepository;
    
    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }
    

    public Order updateOrder(Long id, Order order) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        order.setId(id);
        return orderRepository.save(order);
    }
    
    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found with id: " + id);
        }
        orderRepository.deleteById(id);
    }



    @Transactional
    public Order placeOrder(String customerName, String customerEmail, List<Long> productIds, List<Integer> quantities) {
        // TODO #3: 구현 항목
        // * 주어진 고객 정보로 새 Order를 생성
        // * 지정된 Product를 주문에 추가
        // * order 의 상태를 PENDING 으로 변경
        // * orderDate 를 현재시간으로 설정
        // * order 를 저장
        // * 각 Product 의 재고를 수정
        // * placeOrder 메소드의 시그니처는 변경하지 않은 채 구현하세요.

        Order order = Order.builder()
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(Order.OrderStatus.PENDING)
                .orderDate(LocalDateTime.now())
                .build();

        List<Product> products = productRepository.findAllById(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        if(productMap.size() != productIds.size()) {
            throw new RuntimeException("Product size mismatch");
        }

        for(int i=0; i<productIds.size(); i++) {
            Long productId = productIds.get(i);
            Integer quantity = quantities.get(i);

            Product product = productMap.get(productId);

            if(product == null) {
                throw new RuntimeException("Product not found with id: " + productId);
            }

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(quantity)
                    .price(product.getPrice())
                    .build();

            product.decreaseStock(quantity);
            order.addItem(orderItem);
        }

        return orderRepository.save(order);
    }

    /**
     * TODO #4 (리펙토링): Service 에 몰린 도메인 로직을 도메인 객체 안으로 이동
     * - Repository 조회는 도메인 객체 밖에서 해결하여 의존 차단 합니다.
     * - #3 에서 추가한 도메인 메소드가 있을 경우 사용해도 됩니다.
     */
    @Transactional
    public Order checkoutOrder(String customerName,
                               String customerEmail,
                               List<OrderProduct> orderProducts,
                               String couponCode) {

        if (orderProducts == null || orderProducts.isEmpty()) {
            throw new IllegalArgumentException("orderReqs invalid");
        }

        Order order = Order.of(customerName, customerEmail);

        for (OrderProduct req : orderProducts) {
            Long pid = req.getProductId();
            int qty = req.getQuantity();

            Product product = productRepository.findById(pid)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + pid));
            if (qty <= 0) {
                throw new IllegalArgumentException("quantity must be positive: " + qty);
            }

            OrderItem item = OrderItem.of(order, product, qty, product.getPrice());

            order.addItem(item);
        }
        order.setFinal(couponCode);
        return orderRepository.save(order);
    }

    /**
     * TODO #5: 코드 리뷰 - 장시간 작업과 진행률 저장의 트랜잭션 분리
     * - 시나리오: 일괄 배송 처리 중 진행률을 저장하여 다른 사용자가 조회 가능해야 함.
     * - 리뷰 포인트: proxy 및 transaction 분리, 예외 전파/롤백 범위, 가독성 등
     * - 상식적인 수준에서 요구사항(기획)을 가정하며 최대한 상세히 작성하세요.
     */
    @Transactional
    public void bulkShipOrdersParent(String jobId, List<Long> orderIds) {

        // 해당 코드는 많은 양의 데이터를 한 번의 트랜잭션으로 처리하고 있습니다.
        // 트랜잭션 내의 영속성 객체들은 트랜잭션이 종료될 때 까지 메모리에 유지되고
        // os에서 할당받은 메모리를 거의 다 쓰게 되면 gc가 작동하게 됩니다.
        // gc는 메모리를 정리하기 위해 다른 작업들을 모두 잠시 정지 시키는 stop the world를 행하고
        // 이는 프로그램의 성능 저하로 이어질 수 있습니다.
        // 또한 gc로 인해 메모리가 정리 되더라도 os에서 할당받은 메모리의 총량을 넘어가는 순간
        // 프로그램은 강제 종료가 됩니다.
        
        // 해당 작업이 꼭 한 번에 전부가 처리되어야 하는 작업이 아니라면 한 번의 트랜잭션으로 처리하기 보다는
        // scheduler에서 cron 설정에 따라 작은 단위로 지속적으로 처리하는 방식을 고려해볼 수 있습니다.
        // 또한 트랜잭션 내에서 트랜잭션을 호출하기 보다는 스케쥴러에서 각각의 트랜잭션을 호출하는게
        // 가독성과 흐름제어에 좋다고 생각합니다.

        // 반복문 내에서 작업 1개를 시도하고 성공할 때 마다 업데이트 하기 보다는
        // 한 번에 작은 단위의 작업을 처리하고 한 번에 작은 작업에 대한 상태가 업데이트 됨이 바람직할 것 같습니다.
        
        // 현재 코드는 예외가 발생해도 아무것도 하고 있지 않아보입니다.
        // 예외가 발생한 데이터를 알 수 있도록 catch 구문에서 예외 데이터에 대한 기록을 하고
        // 다음 주기에 다음 주기에 처리될 데이터와 일전에 실패했던 데이터들의 재시도가 이루어지면 좋을 것 같습니다.
        // 다만 이전 주기 데이터의 재실패가 이번 주기의 데이터 성공 유무에 영향을 주면 안되므로
        // 이 트랜잭션들 또한 분리되어야 한다고 생각합니다.

        // 또한 계속 실패가 누적되는 상황이 발생하지 않도록
        // 일정 횟수 이상의 재시도는 하지 않도록 하고 담당자가 직접 확인하도록 해야합니다.

        // 아직 제가 부족하여 proxy에 대한 내용은 잘 알지 못합니다.
        // 다만 다른 누구보다 열심히 배울 수 있다고 생각합니다.
        // 읽어주셔서 감사합니다.

        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> processingStatusRepository.save(ProcessingStatus.builder().jobId(jobId).build()));
        ps.markRunning(orderIds == null ? 0 : orderIds.size());
        processingStatusRepository.save(ps);

        int processed = 0;
        for (Long orderId : (orderIds == null ? List.<Long>of() : orderIds)) {
            try {
                // 오래 걸리는 작업 이라는 가정 시뮬레이션 (예: 외부 시스템 연동, 대용량 계산 등)
                orderRepository.findById(orderId).ifPresent(o -> o.setStatus(Order.OrderStatus.PROCESSING));
                // 중간 진행률 저장
                this.updateProgressRequiresNew(jobId, ++processed, orderIds.size());
            } catch (Exception e) {
            }
        }
        ps = processingStatusRepository.findByJobId(jobId).orElse(ps);
        ps.markCompleted();
        processingStatusRepository.save(ps);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgressRequiresNew(String jobId, int processed, int total) {
        ProcessingStatus ps = processingStatusRepository.findByJobId(jobId)
                .orElseGet(() -> ProcessingStatus.builder().jobId(jobId).build());
        ps.updateProgress(processed, total);
        processingStatusRepository.save(ps);
    }

}