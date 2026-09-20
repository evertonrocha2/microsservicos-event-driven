package br.edu.tp3.contracts;

/**
 * Mensagem que exige ORDEM de processamento dentro de um agregado.
 *
 * O broker nao garante ordem global quando ha varios consumidores concorrentes.
 * Entao a ordem e resolvida em duas frentes:
 *
 *  1. Infraestrutura: as mensagens do mesmo agregado vao para a mesma fila,
 *     e essa fila usa consumidor unico ativo (x-single-active-consumer).
 *  2. Aplicacao: o consumidor guarda o ultimo sequenceNumber processado por agregado
 *     e recusa qualquer mensagem fora de ordem (ver MessageSequenceGuard).
 */
public interface OrderedMessage extends AsyncMessage {

    /** Identidade do agregado que define a "raia" de ordenacao. Aqui, o id do pedido. */
    String aggregateId();

    /** Numero sequencial crescente dentro do agregado. Comeca em 1. */
    long sequenceNumber();
}
