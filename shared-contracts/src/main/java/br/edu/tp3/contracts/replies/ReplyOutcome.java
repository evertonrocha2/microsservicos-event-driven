package br.edu.tp3.contracts.replies;

/** Resultado de um passo da saga. Nao existe meio termo: ou avanca, ou compensa. */
public enum ReplyOutcome {
    SUCCESS,
    FAILURE
}
