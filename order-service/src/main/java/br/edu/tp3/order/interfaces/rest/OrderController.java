package br.edu.tp3.order.interfaces.rest;

import br.edu.tp3.order.application.CreateOrderUseCase;
import br.edu.tp3.order.domain.model.Order;
import br.edu.tp3.order.domain.model.OrderItem;
import br.edu.tp3.order.domain.repository.OrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * API REST sincrona do pedido.
 *
 * Detalhe que costuma passar batido: o POST devolve 202 ACCEPTED, nao 201 CREATED.
 *
 * 202 significa "recebi e vou processar". E a resposta honesta quando o trabalho
 * continua de forma assincrona. Devolver 201 seria mentir, porque neste instante o
 * estoque ainda nao foi reservado e o pagamento ainda nao foi autorizado.
 *
 * O cliente recebe o orderId e acompanha o andamento no GET /api/orders/{id}.
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Pedidos", description = "Criacao e consulta de pedidos")
public class OrderController {

    private final CreateOrderUseCase createOrder;
    private final OrderRepository orderRepository;

    public OrderController(CreateOrderUseCase createOrder, OrderRepository orderRepository) {
        this.createOrder = createOrder;
        this.orderRepository = orderRepository;
    }

    @PostMapping
    @Operation(
            summary = "Cria o pedido e dispara a saga",
            description = "Retorna 202 Accepted. A confirmacao acontece de forma "
                    + "assincrona conforme os passos da saga sao concluidos.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Pedido aceito, saga iniciada"),
            @ApiResponse(responseCode = "400", description = "Dados invalidos")
    })
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(i -> new OrderItem(i.sku(), i.quantity(), i.unitPrice()))
                .toList();

        Order order = createOrder.handle(request.customerId(), items);

        return ResponseEntity
                .accepted()
                .location(URI.create("/api/orders/" + order.getId()))
                .body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta o estado atual do pedido e da saga")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
            @ApiResponse(responseCode = "404", description = "Pedido nao existe")
    })
    public ResponseEntity<OrderResponse> findById(@PathVariable String id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping
    @Operation(summary = "Lista todos os pedidos")
    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::from)
                .toList();
    }
}
