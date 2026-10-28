-- Para reintentar los envíos que fallaron: cuántas veces falló y cuándo toca volver a intentar
ALTER TABLE comprobantes
    ADD COLUMN intentos_fallidos INT      NOT NULL DEFAULT 0,
    ADD COLUMN proximo_intento   DATETIME NULL,
    ADD KEY ix_comprobantes_reintento (estado, proximo_intento);
