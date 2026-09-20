-- Padrao "Database per Service": cada microsservico e dono exclusivo do seu schema.
-- Nenhum servico acessa a tabela do outro diretamente, somente via API ou mensagem.
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE inventory_db;
