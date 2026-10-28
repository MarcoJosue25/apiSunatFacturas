-- Series y correlativos. Una fila por tipo de documento y serie (por ejemplo 01 / F001).
CREATE TABLE series (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    tipo_documento     CHAR(2)     NOT NULL,
    serie              CHAR(4)     NOT NULL,
    ultimo_correlativo BIGINT      NOT NULL DEFAULT 0,
    version            BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_series_tipo_serie (tipo_documento, serie)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Cada comprobante emitido. Se guardan el XML firmado y la constancia (CDR) de la SUNAT porque hay que conservarlos.
-- Los ítems viven dentro del XML.
CREATE TABLE comprobantes (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    id_externo           VARCHAR(100)   NOT NULL,
    tipo_documento       CHAR(2)        NOT NULL,
    serie                CHAR(4)        NOT NULL,
    correlativo          BIGINT         NOT NULL,
    fecha_emision        DATETIME       NOT NULL,
    ruc_cliente          CHAR(11)       NOT NULL,
    razon_social_cliente VARCHAR(1500)  NOT NULL,
    importe_total        DECIMAL(14, 2) NOT NULL,
    estado               VARCHAR(30)    NOT NULL,
    codigo_sunat         VARCHAR(10)    NULL,
    descripcion_sunat    VARCHAR(2000)  NULL,
    observaciones        TEXT           NULL,
    xml_firmado          LONGBLOB       NULL,
    cdr_zip              LONGBLOB       NULL,
    version              BIGINT         NOT NULL DEFAULT 0,
    creado_en            DATETIME(3)    NOT NULL,
    actualizado_en       DATETIME(3)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_comprobantes_id_externo (id_externo),
    UNIQUE KEY uk_comprobantes_numero (tipo_documento, serie, correlativo),
    KEY ix_comprobantes_estado (estado)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Cada vez que se intenta enviar un comprobante a la SUNAT queda una fila, con lo que pasó
CREATE TABLE envio_intentos (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    comprobante_id BIGINT        NOT NULL,
    fecha          DATETIME(3)   NOT NULL,
    resultado      VARCHAR(30)   NOT NULL,
    codigo         VARCHAR(10)   NULL,
    descripcion    VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    KEY ix_envio_intentos_comprobante (comprobante_id),
    CONSTRAINT fk_envio_intentos_comprobante FOREIGN KEY (comprobante_id) REFERENCES comprobantes (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
