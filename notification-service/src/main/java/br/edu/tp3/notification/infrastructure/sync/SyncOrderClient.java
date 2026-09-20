package br.edu.tp3.notification.infrastructure.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/**
 * COMUNICACAO SINCRONA com o order-service, para comparar com a assincrona.
 *
 * O que acontece em uma chamada destas:
 *
 *   1. a thread deste servico chama o order-service por HTTP;
 *   2. ela FICA BLOQUEADA esperando a resposta;
 *   3. se o order-service estiver lento, esta thread fica lenta junto;
 *   4. se o order-service estiver fora do ar, esta chamada falha na hora.
 *
 * Isso se chama ACOPLAMENTO TEMPORAL: os dois servicos precisam estar de pe ao
 * mesmo tempo para a operacao funcionar. E o custo que se paga para ter a resposta
 * imediata.
 *
 * Compare com o OrderEventListener no mesmo servico: la, se o order-service cair,
 * nada acontece. As mensagens ficam guardadas na fila e sao consumidas quando ele
 * voltar. O preco daquela resiliencia e nao ter resposta imediata.
 *
 * Nenhum dos dois e melhor. Sao trocas diferentes para problemas diferentes.
 *
 * Detalhe que costuma faltar em codigo real: TIMEOUT. Sem ele, a thread pode ficar
 * presa indefinidamente e, sob carga, o pool de threads esgota. Um servico lento
 * derruba quem depende dele. Por isso chamada sincrona sem timeout e um bug
 * esperando o momento certo de aparecer.
 */
@Component
public class SyncOrderClient {

    private static final Logger log = LoggerFactory.getLogger(SyncOrderClient.class);

    private final RestClient restClient;

    public SyncOrderClient(@Value("${tp3.order-service.base-url}") String baseUrl,
                           @Value("${tp3.order-service.connect-timeout-ms:2000}") int connectTimeout,
                           @Value("${tp3.order-service.read-timeout-ms:3000}") int readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        factory.setReadTimeout(Duration.ofMillis(readTimeout));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * Busca o pedido no order-service e espera a resposta.
     *
     * Uma falha aqui NAO e um detalhe interno. Ela sobe para quem chamou este
     * metodo, e a requisicao do usuario final falha junto. Numa cadeia de tres
     * ou quatro chamadas sincronas, a disponibilidade final e o PRODUTO das
     * disponibilidades de cada elo: quatro servicos com 99% dao 96%.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchOrder(String orderId) {
        long startedAt = System.currentTimeMillis();
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/api/orders/{id}", orderId)
                    .retrieve()
                    .body(Map.class);

            log.info("chamada SINCRONA concluida orderId={} em {}ms",
                    orderId, System.currentTimeMillis() - startedAt);
            return response;

        } catch (RestClientException e) {
            log.error("chamada SINCRONA falhou orderId={} apos {}ms: {}",
                    orderId, System.currentTimeMillis() - startedAt, e.getMessage());
            throw e;
        }
    }
}
