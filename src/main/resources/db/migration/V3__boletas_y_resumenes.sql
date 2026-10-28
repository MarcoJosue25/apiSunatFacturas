-- Los resúmenes diarios de boletas: cada uno junta hasta 500 boletas de una misma fecha de emisión y se informa a la SUNAT con un ticket
CREATE TABLE resumenes_diarios (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    nombre            VARCHAR(20)   NOT NULL,
    fecha_referencia  DATE          NOT NULL,
    correlativo       INT           NOT NULL,
    fecha_generacion  DATE          NOT NULL,
    estado            VARCHAR(30)   NOT NULL,
    ticket            VARCHAR(30)   NULL,
    codigo_sunat      VARCHAR(10)   NULL,
    descripcion_sunat VARCHAR(2000) NULL,
    xml_firmado       LONGBLOB      NULL,
    cdr_zip           LONGBLOB      NULL,
    intentos_fallidos INT           NOT NULL DEFAULT 0,
    proximo_intento   DATETIME      NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    creado_en         DATETIME(3)   NOT NULL,
    actualizado_en    DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resumenes_nombre (nombre),
    UNIQUE KEY uk_resumenes_fecha_correlativo (fecha_referencia, correlativo),
    KEY ix_resumenes_estado (estado, proximo_intento)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Las boletas son comprobantes con tipo 03. El cliente de una boleta puede tener DNI, RUC, carné, pasaporte o ningún documento,
-- así que el RUC del cliente pasa a ser "documento del cliente" con su tipo. Los totales hacen falta para armar el resumen.
ALTER TABLE comprobantes
    CHANGE COLUMN ruc_cliente documento_cliente VARCHAR(20) NOT NULL,
    ADD COLUMN tipo_documento_cliente CHAR(1)        NOT NULL DEFAULT '6',
    ADD COLUMN total_gravado          DECIMAL(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN total_igv              DECIMAL(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN resumen_id             BIGINT         NULL,
    ADD KEY ix_comprobantes_resumen (resumen_id),
    ADD CONSTRAINT fk_comprobantes_resumen FOREIGN KEY (resumen_id) REFERENCES resumenes_diarios (id);
